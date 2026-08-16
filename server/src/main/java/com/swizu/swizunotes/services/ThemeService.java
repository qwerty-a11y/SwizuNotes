/*
 * Copyright (C) 2026 qwerty-a11y
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swizu.swizunotes.services;

import com.swizu.swizunotes.common.exception.BadRequestException;
import com.swizu.swizunotes.common.exception.InternalException;
import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.dto.response.ThemeAdminResponse;
import com.swizu.swizunotes.dto.response.ThemeSummaryResponse;
import com.swizu.swizunotes.entity.StaticResource;
import com.swizu.swizunotes.entity.Theme;
import com.swizu.swizunotes.entity.ThemeStatus;
import com.swizu.swizunotes.repository.StaticResourceRepository;
import com.swizu.swizunotes.repository.ThemeRepository;
import com.nlf.calendar.Lunar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主题管理（配置 + 文件 + 可见性，独立模块）。
 *
 * 数据分布：
 *  - themes 表：配置（显示名/日期自动切换区间/公开状态，常变数据）
 *  - static_resources 表：主题文件登记（CSS 与亮/暗 banner 作为静态资源存储，
 *    id = 文件名，读取走 GET /api/v1/static-resources/{id}，见 StaticResourceController）
 *
 * 文件命名约定（static_resources.id = 文件名，磁盘路径 uploads/themes/）：
 *   CSS   → &lt;主题名&gt;.css
 *   banner → &lt;主题名&gt;-light.&lt;ext&gt; / &lt;主题名&gt;-dark.&lt;ext&gt;
 *
 * 主题 CSS 内用占位符引用背景图（存原文，读取时替换为真实 URL）：
 *   {{BANNER_LIGHT}} → /api/v1/static-resources/&lt;主题名&gt;-light.&lt;ext&gt;
 *   {{BANNER_DARK}}  → /api/v1/static-resources/&lt;主题名&gt;-dark.&lt;ext&gt;
 *
 * 可见性（公开读取路径 + Service 校验，与媒体模块同模式）：
 *   已发布主题文件公开可读；未发布主题文件仅管理员可用预览令牌读取
 *   （10 分钟有效）；无令牌一律按"资源不存在"处理，隐藏存在性。
 *   未发布主题的 CSS 返回前还会把 url() 全部追加 preview_token 查询参数
 *   （浏览器加载 CSS 内资源不会自动带 CSS 自身的 query，必须重写）。
 */
@Service
public class ThemeService {

    /** 主题文件读取结果（资源 + 已推断的媒体类型）；供 StaticResourceController 转发 */
    public record StaticResourceContent(Resource resource, MediaType mediaType) {}

    /** 日期自动切换按中国时区取"今天"（博客面向国内用户，服务器时区可能不同） */
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    /** 默认主题名（内置兜底主题：不可删除、不参与自动轮换、无命中时固定返回） */
    private static final String DEFAULT_THEME_NAME = "default";
    /** 全局深色主题名（保留名：dark.css 按普通静态资源公开，创建主题不可占用，避免劫持其可见性） */
    private static final String DARK_THEME_NAME = "dark";
    /** themes 表无 default 配置时的兜底显示名 */
    private static final String DEFAULT_THEME_DISPLAY = "默认";

    /** CSS 中背景图占位符（管理页上传模板约定） */
    private static final String BANNER_LIGHT_PLACEHOLDER = "{{BANNER_LIGHT}}";
    private static final String BANNER_DARK_PLACEHOLDER = "{{BANNER_DARK}}";

    /** 主题名格式：小写字母开头，字母/数字/连字符，最长 64 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");
    /** 农历日期格式：M-d 或 闰M-d（如 8-15、闰8-15；月 1-12，日 1-30） */
    private static final Pattern LUNAR_PATTERN = Pattern.compile("^(闰)?(1[0-2]|[1-9])-([1-9]|[12][0-9]|30)$");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final long MAX_CSS_BYTES = 512 * 1024;
    private static final long MAX_BANNER_BYTES = 10 * 1024 * 1024;
    private static final MediaType CSS_MEDIA_TYPE = MediaType.parseMediaType("text/css;charset=UTF-8");

    /** 预览令牌有效期 */
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(10);

    /** url(...) 匹配（含引号形式），未公开主题预览时追加预览令牌 */
    private static final Pattern URL_PATTERN = Pattern.compile("url\\(\\s*(['\"]?)([^)'\"\\s]+)\\1\\s*\\)");

    /** 主题文件 id → 主题名推导（文件名命名约定） */
    private static final Pattern THEME_CSS_ID = Pattern.compile("^(.*)\\.css$");
    private static final Pattern THEME_BANNER_ID = Pattern.compile("^(.*)-(light|dark)\\.[a-z0-9]+$");

    /**
     * 农历日期（每年重复）：普通月 m < 闰月 m < 普通月 m+1（键 = 月×2 + 闰月偏移）。
     * 供"今天是否落在农历区间"判断，支持跨年区间（开始 > 结束 = 年末段 + 年初段）。
     */
    private record LunarDate(int month, boolean leap, int day) implements Comparable<LunarDate> {
        private int key() {
            return month * 2 + (leap ? 1 : 0);
        }

        @Override
        public int compareTo(LunarDate o) {
            int c = Integer.compare(key(), o.key());
            return c != 0 ? c : Integer.compare(day, o.day());
        }
    }

    private record PreviewSession(String themeName, Instant expiresAt) {}

    /** 预览令牌表（内存态，重启即失效；过期惰性清理） */
    private final Map<String, PreviewSession> previewSessions = new ConcurrentHashMap<>();

    @Autowired private ThemeRepository themeRepository;
    @Autowired private StaticResourceRepository staticResourceRepository;

    // ============ 主题配置读取 ============

    /**
     * 今天（中国时区）生效的主题：日期配置三选一——无（不参与切换）/ 仅公历区间
     * （[startDate, endDate]，含边界，同日 = 单日）/ 仅农历区间（[lunarStart, lunarEnd]，
     * 每年重复，支持跨年如 12-25 ~ 1-5，同日 = 单日）命中。
     * 候选 = 预发布 + 已发布（未发布不参与；**default 不参与自动轮换**）；
     * 多主题冲突时**后上传（created_at 最新）优先**。
     * **预发布主题首次生效时自动转换为已发布**。
     * 无任何主题命中时**固定返回默认主题**（default 不可删除，始终存在）。
     */
    @Transactional
    public ThemeSummaryResponse getActiveTheme() {
        LocalDate today = LocalDate.now(CHINA_ZONE);
        LunarDate todayLunar = toLunarDate(today);
        List<Theme> candidates =
                themeRepository.findByStatusInOrderByCreatedAtDesc(List.of(ThemeStatus.prerelease, ThemeStatus.published));
        for (Theme theme : candidates) {
            if (theme.getId().equals(DEFAULT_THEME_NAME)) {
                // 默认主题不参与日期自动轮换（只作为无命中时的兜底返回）
                continue;
            }
            if (isSolarActive(theme, today) || isLunarActive(theme, todayLunar)) {
                if (theme.getStatus() == ThemeStatus.prerelease) {
                    // 首次生效：预发布 → 已发布（此后普通用户可随时切换）
                    theme.setStatus(ThemeStatus.published);
                    themeRepository.save(theme);
                }
                return new ThemeSummaryResponse(theme.getId(), theme.getDisplayName());
            }
        }
        return defaultSummary();
    }

    /** 默认主题摘要（themes 表有配置用其显示名，否则写死"默认"） */
    private ThemeSummaryResponse defaultSummary() {
        Theme def = themeRepository.findById(DEFAULT_THEME_NAME).orElse(null);
        return def != null
                ? new ThemeSummaryResponse(def.getId(), def.getDisplayName())
                : new ThemeSummaryResponse(DEFAULT_THEME_NAME, DEFAULT_THEME_DISPLAY);
    }

    private boolean isSolarActive(Theme theme, LocalDate today) {
        LocalDate startDate = theme.getStartDate();
        LocalDate endDate = theme.getEndDate();
        if (startDate == null || endDate == null) {
            // 未配置公历区间（无日期配置）= 不参与日期自动切换
            return false;
        }
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    private boolean isLunarActive(Theme theme, LunarDate today) {
        if (theme.getLunarStart() == null || theme.getLunarEnd() == null) {
            // 未配置农历区间 = 不参与日期自动切换
            return false;
        }
        LunarDate start = parseLunarDate(theme.getLunarStart());
        LunarDate end = parseLunarDate(theme.getLunarEnd());
        if (start == null || end == null) {
            // DB 脏数据（格式异常）：该主题不参与农历匹配，避免一条脏数据拖垮 /themes/active
            return false;
        }
        if (start.compareTo(end) <= 0) {
            return today.compareTo(start) >= 0 && today.compareTo(end) <= 0;
        }
        // 跨年区间（如 12-25 ~ 1-5）：年末段 + 年初段
        return today.compareTo(start) >= 0 || today.compareTo(end) <= 0;
    }

    /**
     * 解析农历日期字符串（正常路径格式已在创建/更新时校验；
     * 此处复核格式，异常值返回 null 由调用方安全跳过）
     */
    private LunarDate parseLunarDate(String value) {
        if (value == null || !LUNAR_PATTERN.matcher(value).matches()) {
            return null;
        }
        boolean leap = value.startsWith("闰");
        String[] parts = (leap ? value.substring(1) : value).split("-");
        return new LunarDate(Integer.parseInt(parts[0]), leap, Integer.parseInt(parts[1]));
    }

    /**
     * 公历日期 → 农历（lunar-java：闰月 getMonth() 为负值）。
     * 用 fromYmd 按 LocalDate 分量直接构造：日期已在 CHINA_ZONE 定好，
     * 不经过 Date/UTC Instant 中转，避免 lunar-java 内部按 JVM 默认时区解析导致偏移一天。
     */
    private LunarDate toLunarDate(LocalDate date) {
        Lunar lunar = Lunar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        int month = lunar.getMonth();
        return new LunarDate(Math.abs(month), month < 0, lunar.getDay());
    }

    /** 已发布主题列表（导航栏主题选择器；预发布/未发布对普通用户不可见） */
    @Transactional(readOnly = true)
    public List<ThemeSummaryResponse> getPublicThemes() {
        return themeRepository.findByStatusOrderByCreatedAtDesc(ThemeStatus.published).stream()
                .map(t -> new ThemeSummaryResponse(t.getId(), t.getDisplayName()))
                .toList();
    }

    /** 管理页主题列表（含未公开，新上传的在前） */
    @Transactional(readOnly = true)
    public List<ThemeAdminResponse> getAllThemes() {
        return themeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    // ============ 主题文件读取（StaticResourceController 转发） ============

    /**
     * 尝试按主题文件读取（id 匹配命名约定且 themes 表有配置时）：
     *  - 非主题文件 → Optional.empty()（交由静态资源白名单逻辑处理）
     *  - 主题文件 → 可见性校验（已发布公开；未发布需有效预览令牌，否则 404 隐藏存在性），
     *    CSS 先替换占位符（未发布再重写 url() 追加预览令牌），banner 直接流式返回
     */
    @Transactional(readOnly = true)
    public Optional<StaticResourceContent> tryReadThemeFile(String id, String previewToken) {
        String themeName = themeNameOfFileId(id);
        if (themeName == null) {
            return Optional.empty();
        }
        Theme theme = themeRepository.findById(themeName).orElse(null);
        if (theme == null) {
            // 命中主题文件命名约定但主题配置不存在（残留文件）：
            // 按"资源不存在"隐藏存在性，绝不回落到公开白名单
            throw new ResourceNotFoundException("资源不存在");
        }
        requireThemeAccessible(theme, previewToken);
        StaticResource row = staticResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
        if (id.endsWith(".css")) {
            String css = readThemeCss(theme, previewToken, id);
            return Optional.of(new StaticResourceContent(
                    new ByteArrayResource(css.getBytes(StandardCharsets.UTF_8)), CSS_MEDIA_TYPE));
        }
        Path path = Path.of(row.getPath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("资源不存在");
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(id)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return Optional.of(new StaticResourceContent(new FileSystemResource(path), mediaType));
    }

    // ============ 主题管理 ============

    /**
     * 创建主题（配置进 themes 表；CSS/亮暗 banner 文件**可选**——可先建空壳主题，
     * 文件在后续更新中上传；文件参数非空时才落盘并登记 static_resources）
     */
    @Transactional
    public ThemeAdminResponse createTheme(String name, String displayName, LocalDate startDate, LocalDate endDate,
                                          String lunarStart, String lunarEnd, String status,
                                          MultipartFile cssFile,
                                          MultipartFile bannerLight, MultipartFile bannerDark) {
        ThemeStatus themeStatus = parseStatus(status);
        // 默认主题为内置兜底：不参与自动轮换（日期一律忽略，值不保存）、发布状态固定 published（不接受更改）
        if (name.equals(DEFAULT_THEME_NAME)) {
            startDate = null;
            endDate = null;
            lunarStart = null;
            lunarEnd = null;
            themeStatus = ThemeStatus.published;
        }
        validateMeta(name, displayName, startDate, endDate, lunarStart, lunarEnd, themeStatus);
        if (themeRepository.existsById(name)) {
            throw new BadRequestException("主题已存在");
        }
        // 预发布/已发布必须本次创建时即上传亮/暗封面（名称已由 validateMeta 保证非空）
        if (themeStatus == ThemeStatus.prerelease || themeStatus == ThemeStatus.published) {
            if (bannerLight == null || bannerLight.isEmpty()) {
                throw new BadRequestException("预发布/已发布需上传亮色封面背景图");
            }
            if (bannerDark == null || bannerDark.isEmpty()) {
                throw new BadRequestException("预发布/已发布需上传暗色封面背景图");
            }
        }

        Theme theme = new Theme();
        theme.setId(name);
        theme.setDisplayName(displayName.trim());
        theme.setStartDate(startDate);
        theme.setEndDate(endDate);
        theme.setLunarStart(lunarStart);
        theme.setLunarEnd(lunarEnd);
        theme.setStatus(themeStatus == null ? ThemeStatus.unpublished : themeStatus);
        theme.setCreatedAt(Instant.now());
        try {
            if (cssFile != null && !cssFile.isEmpty()) {
                registerStaticResource(name + ".css", storeThemeFile(name + ".css", cssFile, true));
            }
            if (bannerLight != null && !bannerLight.isEmpty()) {
                String lightId = bannerFileId(name, "light", bannerLight);
                registerStaticResource(lightId, storeThemeFile(lightId, bannerLight, false));
            }
            if (bannerDark != null && !bannerDark.isEmpty()) {
                String darkId = bannerFileId(name, "dark", bannerDark);
                registerStaticResource(darkId, storeThemeFile(darkId, bannerDark, false));
            }
        } catch (RuntimeException e) {
            deleteThemeFiles(name);
            throw e;
        }
        try {
            return toAdminResponse(themeRepository.save(theme));
        } catch (RuntimeException e) {
            deleteThemeFiles(name);
            throw e;
        }
    }

    /** 更新主题配置；字段均可选（不传显示名保留原值，日期/农历不传 = 清空）；文件参数为空表示保留原文件 */
    @Transactional
    public ThemeAdminResponse updateTheme(String name, String displayName, LocalDate startDate, LocalDate endDate,
                                          String lunarStart, String lunarEnd, String status,
                                          MultipartFile cssFile,
                                          MultipartFile bannerLight, MultipartFile bannerDark) {
        Theme theme = themeRepository.findById(name)
                .orElseThrow(() -> new ResourceNotFoundException("主题不存在"));
        // 更新时显示名可选：仅在非空时校验/更新（不传保留原值）
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BadRequestException("主题名需为小写字母开头的字母/数字/连字符组合（最长 64 位）");
        }
        if (DARK_THEME_NAME.equals(name)) {
            throw new BadRequestException("\"dark\" 为保留主题名（全局深色模式），不可创建");
        }
        if (displayName != null && !displayName.isBlank() && displayName.trim().length() > 50) {
            throw new BadRequestException("显示名不能为空且不超过 50 字");
        }
        // 默认主题为内置兜底：不参与自动轮换（日期一律忽略，值不保存）、发布状态固定 published（不接受更改）
        ThemeStatus themeStatus = parseStatus(status);
        if (name.equals(DEFAULT_THEME_NAME)) {
            startDate = null;
            endDate = null;
            lunarStart = null;
            lunarEnd = null;
            themeStatus = ThemeStatus.published;
        }
        validateDateConfig(startDate, endDate, lunarStart, lunarEnd);
        if (themeStatus == ThemeStatus.prerelease
                && startDate == null && endDate == null && lunarStart == null && lunarEnd == null) {
            throw new BadRequestException("预发布状态需配置日期区间（公历或农历），否则无法自动生效");
        }
        // 状态硬约束（在文件落盘之前校验，失败不残留文件）：
        // 预发布/已发布必须有名有封面（亮+暗），封面 = 本次传入文件或云端已有登记
        ThemeStatus finalStatus = themeStatus != null ? themeStatus : theme.getStatus();
        if (finalStatus == ThemeStatus.prerelease || finalStatus == ThemeStatus.published) {
            String finalDisplayName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : theme.getDisplayName();
            if (finalDisplayName == null || finalDisplayName.isBlank()) {
                throw new BadRequestException("主题名称不能为空");
            }
            boolean lightOk = (bannerLight != null && !bannerLight.isEmpty())
                    || findBannerFileName(name, "light") != null;
            boolean darkOk = (bannerDark != null && !bannerDark.isEmpty())
                    || findBannerFileName(name, "dark") != null;
            if (!lightOk) {
                throw new BadRequestException("预发布/已发布需先上传亮色封面背景图");
            }
            if (!darkOk) {
                throw new BadRequestException("预发布/已发布需先上传暗色封面背景图");
            }
        }

        if (displayName != null && !displayName.isBlank()) {
            theme.setDisplayName(displayName.trim());
        }
        theme.setStartDate(startDate);
        theme.setEndDate(endDate);
        theme.setLunarStart(lunarStart);
        theme.setLunarEnd(lunarEnd);
        if (themeStatus != null) {
            theme.setStatus(themeStatus);
        }
        // 文件落盘/登记与 DB 保存：异常时补偿删除本次新建的磁盘文件
        // （static_resources 登记随事务回滚；磁盘文件需手动清理，与决策 6 一致）
        List<String> storedPaths = new ArrayList<>();
        try {
            if (cssFile != null && !cssFile.isEmpty()) {
                String newPath = storeThemeFile(name + ".css", cssFile, true);
                storedPaths.add(newPath);
                registerStaticResource(name + ".css", newPath);
            }
            if (bannerLight != null && !bannerLight.isEmpty()) {
                storedPaths.add(replaceBannerFile(name, "light", bannerLight));
            }
            if (bannerDark != null && !bannerDark.isEmpty()) {
                storedPaths.add(replaceBannerFile(name, "dark", bannerDark));
            }
            return toAdminResponse(themeRepository.save(theme));
        } catch (RuntimeException e) {
            for (String path : storedPaths) {
                deleteDiskFile(path);
            }
            throw e;
        }
    }

    /** 删除主题（配置 + 文件登记 + 磁盘文件）；**default 为内置兜底主题，不可删除** */
    @Transactional
    public void deleteTheme(String name) {
        if (DEFAULT_THEME_NAME.equals(name)) {
            throw new BadRequestException("默认主题不可删除");
        }
        themeRepository.findById(name)
                .orElseThrow(() -> new ResourceNotFoundException("主题不存在"));
        themeRepository.deleteById(name);
        deleteThemeFiles(name);
    }

    // ============ 预览令牌 ============

    /** 生成预览令牌（未公开主题仅供管理员预览；10 分钟有效，重启失效） */
    public String createPreviewToken(String name) {
        if (!themeRepository.existsById(name)) {
            throw new ResourceNotFoundException("主题不存在");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        previewSessions.put(token, new PreviewSession(name, Instant.now().plus(PREVIEW_TTL)));
        return token;
    }

    // ============ 内部 ============

    /** 主题可见性：已发布放行；预发布（生效前）/未发布需有效预览令牌（否则按不存在处理，隐藏存在性） */
    private void requireThemeAccessible(Theme theme, String previewToken) {
        if (theme.getStatus() == ThemeStatus.published) {
            return;
        }
        if (!isValidPreviewToken(theme.getId(), previewToken)) {
            throw new ResourceNotFoundException("主题不存在");
        }
    }

    private boolean isValidPreviewToken(String name, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        PreviewSession session = previewSessions.get(token);
        if (session == null) {
            return false;
        }
        if (!session.themeName().equals(name) || session.expiresAt().isBefore(Instant.now())) {
            previewSessions.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 读取主题 CSS（fileId = 主 CSS `<主题名>.css` 或深浅变体 CSS `<主题名>-dark.css`）：
     * 替换占位符为真实 banner URL（占位符始终按主题替换，变体用暗 banner 由 CSS 内容决定）；
     * 未发布时再重写 url() 追加预览令牌。
     */
    private String readThemeCss(Theme theme, String previewToken, String fileId) {
        StaticResource cssRow = staticResourceRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("主题不存在"));
        Path path = Path.of(cssRow.getPath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("主题不存在");
        }
        try {
            String css = Files.readString(path, StandardCharsets.UTF_8);
            css = replaceBannerPlaceholders(theme.getId(), css);
            if (theme.getStatus() != ThemeStatus.published) {
                css = rewritePreviewUrls(css, previewToken);
            }
            return css;
        } catch (IOException e) {
            throw new InternalException("主题读取失败");
        }
    }

    private String replaceBannerPlaceholders(String themeName, String css) {
        if (css.contains(BANNER_LIGHT_PLACEHOLDER)) {
            css = css.replace(BANNER_LIGHT_PLACEHOLDER,
                    "/api/v1/static-resources/" + findBannerFileId(themeName, "light"));
        }
        if (css.contains(BANNER_DARK_PLACEHOLDER)) {
            css = css.replace(BANNER_DARK_PLACEHOLDER,
                    "/api/v1/static-resources/" + findBannerFileId(themeName, "dark"));
        }
        return css;
    }

    private String findBannerFileId(String themeName, String kind) {
        return staticResourceRepository.findByIdStartingWith(themeName + "-").stream()
                .map(StaticResource::getId)
                .filter(id -> id.startsWith(themeName + "-" + kind + "."))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("主题不存在"));
    }

    /** 未公开 CSS 的 url() 全部追加 preview_token（浏览器加载 CSS 内资源不带 CSS 自身的 query） */
    private String rewritePreviewUrls(String css, String token) {
        Matcher matcher = URL_PATTERN.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String sep = url.contains("?") ? "&" : "?";
            String rewritten = "url(" + matcher.group(1) + url + sep + "preview_token=" + token + matcher.group(1) + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rewritten));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 文件 id → 主题名（无匹配返回 null = 普通静态资源） */
    private String themeNameOfFileId(String id) {
        Matcher css = THEME_CSS_ID.matcher(id);
        if (css.matches()) {
            String base = css.group(1);
            // 全局深色主题文件（dark.css）按普通静态资源处理，不归属任何主题
            if (base.equals(DARK_THEME_NAME)) {
                return null;
            }
            // 深浅变体 CSS（<浅色名>-dark.css）：归属对应浅色主题（可见性跟随主题）
            if (base.endsWith("-dark")) {
                String light = base.substring(0, base.length() - "-dark".length());
                if (!light.isEmpty() && themeRepository.existsById(light)) {
                    return light;
                }
            }
            return base;
        }
        Matcher banner = THEME_BANNER_ID.matcher(id);
        if (banner.matches()) {
            // 全局深色主题文件（dark-light.* / dark-dark.*）不归属任何主题
            String name = banner.group(1);
            return name.equals(DARK_THEME_NAME) ? null : name;
        }
        return null;
    }

    private void validateMeta(String name, String displayName, LocalDate startDate, LocalDate endDate,
                              String lunarStart, String lunarEnd, ThemeStatus status) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BadRequestException("主题名需为小写字母开头的字母/数字/连字符组合（最长 64 位）");
        }
        if (DARK_THEME_NAME.equals(name)) {
            throw new BadRequestException("\"dark\" 为保留主题名（全局深色模式），不可创建");
        }
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 50) {
            throw new BadRequestException("显示名不能为空且不超过 50 字");
        }
        validateDateConfig(startDate, endDate, lunarStart, lunarEnd);
        // 预发布必须配置日期区间（公历或农历），否则永远无法自动生效转换
        if (status == ThemeStatus.prerelease
                && startDate == null && endDate == null && lunarStart == null && lunarEnd == null) {
            throw new BadRequestException("预发布状态需配置日期区间（公历或农历），否则无法自动生效");
        }
    }

    /** 解析发布状态字符串（unpublished/prerelease/published，大小写不敏感）；空 = null */
    private ThemeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ThemeStatus.valueOf(status.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("发布状态需为 unpublished/prerelease/published");
        }
    }

    /**
     * 日期配置三选一（创建/更新统一校验）：
     *  - 无：四个日期全部为 null（不参与自动切换）
     *  - 仅公历：startDate + endDate 成对（可为同一天 = 单日）
     *  - 仅农历：lunarStart + lunarEnd 成对（可为同一天 = 单日）
     * 禁止公历与农历同时配置。
     */
    private void validateDateConfig(LocalDate startDate, LocalDate endDate, String lunarStart, String lunarEnd) {
        boolean hasSolar = startDate != null || endDate != null;
        boolean hasLunar = lunarStart != null || lunarEnd != null;
        if (hasSolar && hasLunar) {
            throw new BadRequestException("公历与农历日期区间不能同时配置，只能二选一");
        }
        if (hasSolar) {
            if (startDate == null || endDate == null) {
                throw new BadRequestException("公历区间需同时填写开始与结束日期（可为同一天）");
            }
            if (startDate.isAfter(endDate)) {
                throw new BadRequestException("开始日期不能晚于结束日期");
            }
        }
        if (hasLunar) {
            if (lunarStart == null || lunarEnd == null) {
                throw new BadRequestException("农历区间需同时填写开始与结束日期（可为同一天）");
            }
            validateLunar(lunarStart, lunarEnd);
        }
    }

    /** 农历日期格式校验：M-d 或 闰M-d（月 1-12，日 1-30） */
    private void validateLunar(String lunarStart, String lunarEnd) {
        if (lunarStart != null && !LUNAR_PATTERN.matcher(lunarStart).matches()) {
            throw new BadRequestException("农历日期格式：M-d 或 闰M-d（如 8-15、闰8-15；月 1-12，日 1-30）");
        }
        if (lunarEnd != null && !LUNAR_PATTERN.matcher(lunarEnd).matches()) {
            throw new BadRequestException("农历日期格式：M-d 或 闰M-d（如 8-15、闰8-15；月 1-12，日 1-30）");
        }
    }

    private String bannerFileId(String name, String kind, MultipartFile file) {
        return name + "-" + kind + "." + extensionOf(file.getOriginalFilename());
    }

    private String extensionOf(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot + 1) : "";
    }

    /**
     * 校验并落盘主题文件（uploads/themes/），返回磁盘路径。
     * CSS：读取 → 校验（.css 后缀、≤512KB、含 :root）→ **净化**（剥离外域引用，见 sanitizeCss）→ 落盘；
     * banner：校验图片格式/大小后原样落盘。
     */
    private String storeThemeFile(String fileName, MultipartFile file, boolean isCss) {
        String ext = extensionOf(file.getOriginalFilename());
        if (isCss) {
            if (!ext.equals("css")) {
                throw new BadRequestException("CSS 文件需为 .css 后缀");
            }
            if (file.getSize() > MAX_CSS_BYTES) {
                throw new BadRequestException("CSS 文件不能超过 512KB");
            }
            try {
                String css = new String(file.getBytes(), StandardCharsets.UTF_8);
                if (!css.contains(":root")) {
                    throw new BadRequestException("CSS 文件需包含 :root 变量定义");
                }
                css = sanitizeCss(css);
                Path path = themeDir().resolve(fileName);
                Files.writeString(path, css, StandardCharsets.UTF_8);
                return path.toString();
            } catch (IOException e) {
                throw new InternalException("主题文件保存失败");
            }
        } else {
            if (!IMAGE_EXTENSIONS.contains(ext)) {
                throw new BadRequestException("背景图需为图片文件（png/jpg/jpeg/webp/gif）");
            }
            if (file.getSize() > MAX_BANNER_BYTES) {
                throw new BadRequestException("背景图不能超过 10MB");
            }
            try {
                Path path = themeDir().resolve(fileName);
                file.transferTo(path);
                return path.toString();
            } catch (IOException e) {
                throw new InternalException("主题文件保存失败");
            }
        }
    }

    /**
     * 主题 CSS 净化（安全加固方案 a）：**剥离外域引用**，仅保留站内（/、./、../ 相对路径）与 data: 资源。
     *  - 外域 @import（http/https/协议相对 //）整条删除；站内 @import 保留
     *  - url() 中的外域地址替换为空（该声明失效，浏览器忽略）
     * 目的：防止主题 CSS 让访客浏览器向第三方站点发起请求（外带信息/诱导请求）。
     */
    private String sanitizeCss(String css) {
        // 外域 @import：@import url("http...") / @import url(//...) / @import "http..." / @import '//...'
        css = css.replaceAll("(?i)@import\\s+url\\(\\s*['\"]?(?:https?:)?//[^)'\"\\s]+['\"]?\\s*\\)\\s*;?", "")
                .replaceAll("(?i)@import\\s+['\"](?:https?:)?//[^'\"]+['\"]\\s*;?", "");
        // url() 外域引用 → 空（保留其余内容，声明因值无效被浏览器忽略）
        Matcher matcher = URL_PATTERN.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
                matcher.appendReplacement(sb, "");
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void registerStaticResource(String id, String path) {
        StaticResource row = new StaticResource();
        row.setId(id);
        row.setPath(path);
        staticResourceRepository.save(row);
    }

    /** 替换 banner 文件：新文件落盘 + 登记；旧 id 不同时删除旧登记与旧磁盘文件；返回新文件磁盘路径 */
    private String replaceBannerFile(String themeName, String kind, MultipartFile file) {
        String newId = bannerFileId(themeName, kind, file);
        String newPath = storeThemeFile(newId, file, false);
        registerStaticResource(newId, newPath);
        String oldPrefix = themeName + "-" + kind + ".";
        for (StaticResource old : staticResourceRepository.findByIdStartingWith(oldPrefix)) {
            if (!old.getId().equals(newId)) {
                staticResourceRepository.delete(old);
                deleteDiskFile(old.getPath());
            }
        }
        return newPath;
    }

    private Path themeDir() {
        Path dir = Paths.get("uploads", "themes");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new InternalException("主题目录创建失败");
        }
        return dir;
    }

    /**
     * 删除主题全部文件：static_resources 登记（主 CSS + 深浅变体 CSS + banner）+ 磁盘文件。
     * 只精确匹配本主题的文件 id（<name>.css / <name>-dark.css / <name>-light.<ext> /
     * <name>-dark.<ext>），避免主题名互为前缀（如 a 与 a-b）时误删其他主题的文件。
     */
    private void deleteThemeFiles(String name) {
        staticResourceRepository.findById(name + ".css").ifPresent(row -> {
            staticResourceRepository.delete(row);
            deleteDiskFile(row.getPath());
        });
        for (String prefix : List.of(name + "-light.", name + "-dark.")) {
            for (StaticResource row : staticResourceRepository.findByIdStartingWith(prefix)) {
                staticResourceRepository.delete(row);
                deleteDiskFile(row.getPath());
            }
        }
    }

    private void deleteDiskFile(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException ignored) {
            // 删除失败不阻断（文件残留由人工清理）
        }
    }

    private ThemeAdminResponse toAdminResponse(Theme theme) {
        return new ThemeAdminResponse(theme.getId(), theme.getDisplayName(),
                theme.getStartDate(), theme.getEndDate(), theme.getLunarStart(), theme.getLunarEnd(),
                findBannerFileName(theme.getId(), "light"), findBannerFileName(theme.getId(), "dark"),
                theme.getStatus(), theme.getCreatedAt());
    }

    /** 查主题 banner 文件登记名（如 mid-autumn-light.png）；文件缺失返回 null（管理页资源预览用） */
    private String findBannerFileName(String themeName, String kind) {
        return staticResourceRepository.findByIdStartingWith(themeName + "-").stream()
                .map(StaticResource::getId)
                .filter(id -> id.startsWith(themeName + "-" + kind + "."))
                .findFirst()
                .orElse(null);
    }
}

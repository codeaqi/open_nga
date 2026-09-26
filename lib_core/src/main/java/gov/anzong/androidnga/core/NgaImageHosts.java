package gov.anzong.androidnga.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NGA 图片域名，集中在这一处。
 *
 * 2026-09 NGA 下线了 {@code img*.nga.178.com} 这组图片域名（公共 DNS 直接返回
 * 不存在），附件、版面图标、表情全部改由 {@code img.nga.cn} / {@code img4.nga.cn}
 * 提供，路径不变。服务端下发的帖子数据里已经是新域名了。
 *
 * 光改常量还不够：正文里有人直接贴了完整的旧地址，缓存在本地的老帖子里也全是旧地址，
 * 所以解码时还要用 {@link #rewriteRetiredHosts} 把这些改写过来。
 *
 * 注意主站 {@code nga.178.com} 还活着，只动图片子域。
 */
public final class NgaImageHosts {

    /** 帖子附件 */
    public static final String ATTACHMENT_HOST = "img.nga.cn";

    /** 版面图标、表情等静态资源 */
    public static final String STATIC_HOST = "img4.nga.cn";

    /**
     * 只匹配处在主机名位置（紧跟 {@code ://}）的旧图片子域，
     * 免得误伤路径里恰好出现这串字符的地址。
     */
    private static final Pattern RETIRED_HOST = Pattern.compile("(?<=://)img(\\d*)\\.nga\\.178\\.com");

    private NgaImageHosts() {
    }

    /**
     * 把文本里的旧图片域名换成新的。
     *
     * img4 在新域名下仍是 img4；其余编号子域（img、img6……）统一归到
     * {@link #ATTACHMENT_HOST}——实测 img6.nga.cn 上取不到附件。
     */
    public static String rewriteRetiredHosts(String text) {
        if (text == null || text.indexOf(".nga.178.com") < 0) {
            return text;
        }
        Matcher m = RETIRED_HOST.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String host = "4".equals(m.group(1)) ? STATIC_HOST : ATTACHMENT_HOST;
            m.appendReplacement(sb, host);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

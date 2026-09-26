package gov.anzong.androidnga.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NgaImageHostsTest {

    @Test
    public void attachmentHostMovesToNgaCn() {
        assertEquals("[img]http://img.nga.cn/attachments/mon_202603/24/a.jpg[/img]",
                NgaImageHosts.rewriteRetiredHosts(
                        "[img]http://img.nga.178.com/attachments/mon_202603/24/a.jpg[/img]"));
    }

    /** img4 放的是图标和表情，新域名下同样是 img4，路径不变 */
    @Test
    public void img4KeepsItsOwnSubdomain() {
        assertEquals("https://img4.nga.cn/ngabbs/post/smile/ac0.png",
                NgaImageHosts.rewriteRetiredHosts("https://img4.nga.178.com/ngabbs/post/smile/ac0.png"));
    }

    /** img6.nga.cn 上取不到附件（实测 404），编号子域一律归到 img.nga.cn */
    @Test
    public void otherNumberedSubdomainsFallBackToImg() {
        assertEquals("http://img.nga.cn/attachments/mon_1/b.png",
                NgaImageHosts.rewriteRetiredHosts("http://img6.nga.178.com/attachments/mon_1/b.png"));
    }

    @Test
    public void rewritesEveryOccurrence() {
        String in = "<img src='http://img.nga.178.com/attachments/x.jpg'>"
                + "<img src='http://img4.nga.178.com/y.png'>";
        String out = "<img src='http://img.nga.cn/attachments/x.jpg'>"
                + "<img src='http://img4.nga.cn/y.png'>";
        assertEquals(out, NgaImageHosts.rewriteRetiredHosts(in));
    }

    /** 主站 nga.178.com 还活着，只动图片子域 */
    @Test
    public void leavesMainSiteAndOtherHostsAlone() {
        String in = "https://nga.178.com/read.php?tid=1 http://img.ngacn.cc/attachments/a.mp3 "
                + "https://example.com/img.nga.178.com.png";
        assertEquals(in, NgaImageHosts.rewriteRetiredHosts(in));
    }

    @Test
    public void nullAndEmptyPassThrough() {
        assertNull(NgaImageHosts.rewriteRetiredHosts(null));
        assertEquals("", NgaImageHosts.rewriteRetiredHosts(""));
    }
}

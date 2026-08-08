package gov.anzong.androidnga.activity.compose.stock

import gov.anzong.androidnga.activity.compose.stock.data.PriceGap
import gov.anzong.androidnga.activity.compose.stock.data.gapTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceGapTest {

    @Test
    fun `还没跌到目标价时给出跌幅和差价`() {
        // 长江电力：现价 27.75，建仓 25.00
        val gap = gapTo(27.75f, 25.00f)
        assertEquals(PriceGap(-9.91f, -2.75f), gap!!.round())
    }

    @Test
    fun `档位越低要跌的越多`() {
        assertEquals(PriceGap(-18.92f, -5.25f), gapTo(27.75f, 22.50f)!!.round())
        assertEquals(PriceGap(-27.03f, -7.50f), gapTo(27.75f, 20.25f)!!.round())
    }

    @Test
    fun `已经跌到目标价返回 null`() {
        assertNull(gapTo(25.00f, 25.00f))
        assertNull(gapTo(24.00f, 25.00f))
    }

    @Test
    fun `价格无效返回 null`() {
        // 停牌或还没拉到行情
        assertNull(gapTo(0f, 25.00f))
        // 该档没设置
        assertNull(gapTo(27.75f, 0f))
    }

    /** 浮点直接比会因末位误差挂掉，比到小数点后两位就够了 */
    private fun PriceGap.round() = PriceGap(
        percent = Math.round(percent * 100) / 100f,
        diff = Math.round(diff * 100) / 100f
    )
}

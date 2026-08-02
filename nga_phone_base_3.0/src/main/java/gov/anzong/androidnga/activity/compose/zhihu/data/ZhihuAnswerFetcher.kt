package gov.anzong.androidnga.activity.compose.zhihu.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alibaba.fastjson.JSON
import gov.anzong.androidnga.common.util.NLog
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 知乎回答抓取器。
 *
 * 知乎对 OkHttp/curl 这类裸 HTTP 请求一律返回 403，只有完整的浏览器环境能拿到
 * 内容。所以这里用一个**不加入视图树的隐藏 WebView** 去加载问题页，等页面渲染
 * 完再注入 JS 把数据抽出来回传，最后交给 Compose 原生渲染——用户看不到网页，
 * 也就没有「打开 App」引流和广告。
 *
 * 数据优先取页面内嵌的 js-initialData（结构化 JSON，含作者/赞同数/正文 HTML），
 * 取不到再退回 DOM 抓取。
 *
 * 必须在主线程创建和使用 WebView。
 */
object ZhihuAnswerFetcher {

    private const val TAG = "ZhihuAnswerFetcher"

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 页面加载完到开始抽取之间的等待，留给前端把回答渲染出来 */
    private const val EXTRACT_DELAY_MS = 4000L

    /** 整体超时，超时按空结果处理 */
    private const val TIMEOUT_MS = 20000L

    /** 每页回答数 */
    const val PAGE_SIZE = 10

    /**
     * 加载问题页并抽取回答。挂起直到拿到结果或超时。
     * 返回 null 表示抓取失败（网络问题或页面结构变了）。
     *
     * [offset] > 0 时表示加载更多，直接在页面内调接口，不重新加载页面。
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(
        context: Context,
        url: String,
        offset: Int = 0,
        limit: Int = PAGE_SIZE
    ): ZhihuAnswerResult? =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            var finished = false

            // 统一的收尾：保证 WebView 一定被销毁，且只回调一次
            fun finish(result: ZhihuAnswerResult?) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                webView?.let {
                    it.stopLoading()
                    it.destroy()
                }
                webView = null
                if (cont.isActive) {
                    cont.resume(result)
                }
            }

            handler.post {
                if (finished) return@post
                // debug 包开启 WebView 远程调试，方便用 chrome://inspect 排查抓取问题
                if (gov.anzong.androidnga.BuildConfig.DEBUG) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                val wv = WebView(context.applicationContext)
                webView = wv
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // 不要关图片加载：知乎前端会等资源就绪才渲染回答，
                    // 关掉反而可能让页面停在骨架屏状态抽不到内容。
                    userAgentString = MOBILE_UA
                }
                // JS 算完后通过这个桥回传结果（脚本里有 await，不能用 evaluateJavascript 的返回值）
                wv.addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onResult(json: String?) {
                        handler.post { finish(parseJson(json)) }
                    }

                    /** JS 侧的诊断输出，单独打一条，避免被结果 JSON 挤掉 */
                    @android.webkit.JavascriptInterface
                    fun onLog(msg: String?) {
                        NLog.e(TAG, "JS: $msg")
                    }
                }, "NgaBridge")

                wv.webViewClient = object : WebViewClient() {
                    /**
                     * 知乎页面会主动 redirect 到 zhihu:// 拉起 App，必须拦掉，
                     * 否则抓取过程被打断（日志里能看到 "redirect to pull App..."）。
                     */
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest
                    ): Boolean {
                        val scheme = request.url.scheme
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        if (finished) return
                        // 等前端把回答渲染出来再抽
                        handler.postDelayed({
                            if (finished) return@postDelayed
                            // 脚本里有 await（要调接口取更多回答），evaluateJavascript
                            // 不会等 Promise，所以让 JS 算完主动通过 bridge 回传。
                            val script = "var OFFSET=$offset, LIMIT=$limit;\n$EXTRACT_JS"
                            view.evaluateJavascript(script, null)
                        }, EXTRACT_DELAY_MS)
                    }
                }
                wv.loadUrl(url)
            }

            handler.postDelayed({
                NLog.e(TAG, "fetch timeout: $url")
                finish(null)
            }, TIMEOUT_MS)

            cont.invokeOnCancellation {
                handler.post { finish(null) }
            }
        }

    /**
     * 抓某条回答下的评论。
     *
     * 同样要在知乎页面的同源环境里发请求，所以复用整套隐藏 WebView 流程：
     * 加载该回答的页面，再在页面内调评论接口。
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchComments(
        context: Context,
        answerId: String,
        questionUrl: String
    ): List<ZhihuComment>? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var finished = false

        fun finish(result: List<ZhihuComment>?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            webView?.let {
                it.stopLoading()
                it.destroy()
            }
            webView = null
            if (cont.isActive) {
                cont.resume(result)
            }
        }

        handler.post {
            if (finished) return@post
            val wv = WebView(context.applicationContext)
            webView = wv
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = MOBILE_UA
            }
            wv.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onResult(json: String?) {
                    handler.post { finish(parseComments(json)) }
                }

                @android.webkit.JavascriptInterface
                fun onLog(msg: String?) {
                    NLog.e(TAG, "JS(comment): $msg")
                }
            }, "NgaBridge")

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (finished) return
                    handler.postDelayed({
                        if (finished) return@postDelayed
                        val script = "var ANSWER_ID='$answerId';\n$COMMENT_JS"
                        view.evaluateJavascript(script, null)
                    }, 1500L)
                }
            }
            wv.loadUrl(questionUrl)
        }

        handler.postDelayed({ finish(null) }, TIMEOUT_MS)
        cont.invokeOnCancellation { handler.post { finish(null) } }
    }

    private fun parseComments(json: String?): List<ZhihuComment>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val root = JSON.parseObject(json) ?: return null
            root.getJSONArray("comments")?.mapNotNull { item ->
                val obj = item as? com.alibaba.fastjson.JSONObject ?: return@mapNotNull null
                val content = obj.getString("content").orEmpty()
                if (content.isBlank()) return@mapNotNull null
                ZhihuComment(
                    author = obj.getString("author").orEmpty().ifEmpty { "匿名用户" },
                    content = ZhihuEmoji.replace(content),
                    likeCount = obj.getIntValue("like")
                )
            }.orEmpty()
        } catch (e: Exception) {
            NLog.e(TAG, "parse comments failed: $e")
            null
        }
    }

    /**
     * 评论抽取脚本。知乎有新旧两套评论接口，依次尝试。
     */
    private val COMMENT_JS = """
        (async function(){
          function clean(html){
            if (!html) return '';
            var d = document.createElement('div');
            d.innerHTML = html;
            d.querySelectorAll('script,style').forEach(function(n){ n.remove(); });
            // 知乎表情是 <img alt="[捂脸]">，直接取 innerText 会整个丢掉，
            // 这里换成 alt 里的文字标记，再由原生端映射成 emoji
            d.querySelectorAll('img').forEach(function(n){
              var alt = n.getAttribute('alt') || n.getAttribute('title') || '';
              n.replaceWith(document.createTextNode(alt));
            });
            return (d.innerText || d.textContent || '').trim();
          }
          var out = { comments: [] };
          var urls = [
            '/api/v4/comment_v5/answers/' + ANSWER_ID + '/root_comment?order_by=score&limit=20&offset=',
            '/api/v4/answers/' + ANSWER_ID + '/comments?order=normal&limit=20&offset=0'
          ];
          for (var i = 0; i < urls.length; i++) {
            try {
              var r = await fetch(urls[i], {
                credentials: 'include',
                headers: { 'x-requested-with': 'fetch' }
              });
              NgaBridge.onLog('api[' + i + '] status=' + r.status);
              if (!r.ok) continue;
              var j = await r.json();
              var list = j.data || [];
              NgaBridge.onLog('api[' + i + '] count=' + list.length);
              if (!list.length) continue;
              // 抽样打出含表情的原始 HTML，确认表情到底是 img 还是文本标记
              try {
                for (var s = 0; s < list.length && s < 3; s++) {
                  var hh = list[s].content || '';
                  if (/\[|<img/.test(hh)) {
                    NgaBridge.onLog('emoji raw: ' + hh.substring(0, 260));
                  }
                }
              } catch(e) {}
              out.comments = list.map(function(c){
                var au = '';
                if (c.author) {
                  au = c.author.name || (c.author.member && c.author.member.name) || '';
                }
                return {
                  author: au || '匿名用户',
                  content: clean(c.content || ''),
                  like: c.like_count || c.likeCount || c.vote_count || 0
                };
              }).filter(function(c){ return c.content; });
              if (out.comments.length) break;
            } catch(e) { NgaBridge.onLog('api[' + i + '] err ' + e); }
          }
          try { NgaBridge.onResult(JSON.stringify(out)); } catch(e) {}
        })();
    """

    /** 解析 JS 通过 NgaBridge 回传的结果 JSON */
    private fun parseJson(json: String?): ZhihuAnswerResult? {
        if (json.isNullOrEmpty()) {
            return null
        }
        return try {
            val root = JSON.parseObject(json) ?: return null
            val answers = root.getJSONArray("answers")?.mapNotNull { item ->
                val obj = item as? com.alibaba.fastjson.JSONObject ?: return@mapNotNull null
                val blocks = parseBlocks(obj.getJSONArray("blocks"))
                if (blocks.isEmpty()) return@mapNotNull null
                ZhihuAnswer(
                    id = obj.getString("id").orEmpty(),
                    author = obj.getString("author").orEmpty().ifEmpty { "匿名用户" },
                    headline = obj.getString("headline").orEmpty(),
                    voteCount = obj.getIntValue("voteup"),
                    commentCount = obj.getIntValue("comment"),
                    blocks = blocks
                )
            }.orEmpty()
            ZhihuAnswerResult(
                detailBlocks = parseBlocks(root.getJSONArray("detailBlocks")),
                answers = answers,
                total = root.getIntValue("total"),
                isEnd = root.getBooleanValue("isEnd")
            )
        } catch (e: Exception) {
            NLog.e(TAG, "parse failed: $e")
            null
        }
    }

    /** 把 JS 回传的 blocks 数组转成 ZhihuBlock 列表 */
    private fun parseBlocks(array: com.alibaba.fastjson.JSONArray?): List<ZhihuBlock> {
        if (array == null) return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? com.alibaba.fastjson.JSONObject ?: return@mapNotNull null
            when (obj.getString("type")) {
                "image" -> obj.getString("url")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { ZhihuBlock.ImageBlock(it) }

                else -> obj.getString("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ZhihuBlock.TextBlock(ZhihuEmoji.replace(it)) }
            }
        }
    }

    /**
     * 注入页面的抽取脚本。已在真机上验证过 js-initialData 里能拿到
     * 作者名、赞同数和完整正文 HTML。
     *
     * 正文里的标签转成纯文本：p/div/li 之类块级元素补换行，img 换成「[图片]」占位，
     * 这样原生端按普通文本渲染就有正常段落。
     */
    private val EXTRACT_JS = """
        (async function(){
          // 知乎的图真实地址在 data-original/data-actualsrc，src 往往是懒加载占位图
          function realSrc(img){
            var s = img.getAttribute('data-original')
                 || img.getAttribute('data-actualsrc')
                 || img.getAttribute('data-src')
                 || img.getAttribute('src') || '';
            if (!s) return '';
            if (s.indexOf('//') === 0) s = 'https:' + s;
            if (s.indexOf('http') !== 0) return '';
            // 只过滤确定无意义的：占位 svg、公式图、头像。
            // 不要按 _s/_l/_r 后缀过滤——知乎正文图就是这种命名，会误杀。
            if (/\.svg($|\?)/i.test(s)) return '';
            if (/equation\?tex=/.test(s)) return '';   // 公式图，文字里已有内容
            if (/\/avatar\/|aladdin\/|_avatar/i.test(s)) return '';
            return s;
          }

          /** 把一段正文 HTML 拆成有序的图文块 */
          function toBlocks(html){
            var blocks = [];
            if (!html) return blocks;
            var d = document.createElement('div');
            d.innerHTML = html;
            d.querySelectorAll('script,style,noscript').forEach(function(n){ n.remove(); });
            d.querySelectorAll('br').forEach(function(n){
              n.replaceWith(document.createTextNode('\n'));
            });
            d.querySelectorAll('p,div,li,blockquote,h1,h2,h3,h4').forEach(function(n){
              n.appendChild(document.createTextNode('\n'));
            });

            var buf = '';
            function flush(){
              var t = buf.replace(/\n{3,}/g, '\n\n').trim();
              if (t) blocks.push({ type: 'text', text: t });
              buf = '';
            }
            // 按文档顺序遍历，遇到图片就切段，保证图文顺序和原文一致
            var walker = document.createTreeWalker(d, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, null);
            var node;
            while ((node = walker.nextNode())) {
              if (node.nodeType === 3) {
                buf += node.nodeValue;
              } else if (node.tagName === 'IMG') {
                // 表情图（有 alt 文字标记、尺寸很小）当文字处理，不要单独成图
                var alt = node.getAttribute('alt') || '';
                if (/^\[.{1,8}\]$/.test(alt)) {
                  buf += alt;
                  continue;
                }
                var s = realSrc(node);
                if (s) { flush(); blocks.push({ type: 'image', url: s }); }
                else if (alt) { buf += alt; }
              }
            }
            flush();
            return blocks;
          }

          var res = { detailBlocks: [], answers: [] };

          // 诊断信息，定位抓不到时页面到底是什么状态
          try {
            res.dbg = {
              url: location.href,
              hasInitial: !!document.getElementById('js-initialData'),
              listItem: document.querySelectorAll('.List-item').length,
              answerItem: document.querySelectorAll('.AnswerItem').length
            };
            // 把回答正文 HTML 里的 img 原始属性抽样打出来，确认过滤规则有没有误杀
            var el0 = document.getElementById('js-initialData');
            if (el0) {
              var d0 = JSON.parse(el0.textContent);
              var as0 = (d0.initialState && d0.initialState.entities && d0.initialState.entities.answers) || {};
              var k0 = Object.keys(as0)[0];
              if (k0 && as0[k0] && as0[k0].content) {
                var probe = document.createElement('div');
                probe.innerHTML = as0[k0].content;
                var imgs = probe.querySelectorAll('img');
                res.dbg.imgCount = imgs.length;
                res.dbg.imgSamples = [];
                for (var z = 0; z < imgs.length && z < 3; z++) {
                  res.dbg.imgSamples.push({
                    src: imgs[z].getAttribute('src') || '',
                    orig: imgs[z].getAttribute('data-original') || '',
                    actual: imgs[z].getAttribute('data-actualsrc') || '',
                    picked: realSrc(imgs[z])
                  });
                }
              }
            }
          } catch(e) { res.dbg = { err: String(e) }; }

          // 首选：页面内嵌的结构化 JSON
          try {
            var el = document.getElementById('js-initialData');
            if (el) {
              var data = JSON.parse(el.textContent);
              var ents = (data.initialState && data.initialState.entities) || {};
              var questions = ents.questions || {};
              var answers = ents.answers || {};

              var qk = Object.keys(questions)[0];
              if (qk && questions[qk]) {
                res.detailBlocks = toBlocks(questions[qk].detail || '');
              }
              Object.keys(answers).forEach(function(k){
                var a = answers[k];
                if (!a) return;
                var bs = toBlocks(a.content || a.excerpt || '');
                if (!bs.length) return;
                res.answers.push({
                  author: (a.author && a.author.name) || '匿名用户',
                  headline: (a.author && a.author.headline) || '',
                  voteup: a.voteupCount || 0,
                  comment: a.commentCount || 0,
                  blocks: bs
                });
              });
            }
          } catch(e) {}

          // 兜底：直接从 DOM 抓（js-initialData 结构变了时）
          if (res.answers.length === 0) {
            var items = document.querySelectorAll('.List-item, .AnswerItem');
            for (var i = 0; i < items.length && i < 20; i++) {
              var it = items[i];
              var rc = it.querySelector('.RichText, .RichContent-inner');
              if (!rc) continue;
              var bs = toBlocks(rc.innerHTML);
              if (!bs.length) continue;
              var au = it.querySelector('.AuthorInfo-name, .UserLink-link');
              res.answers.push({
                author: au ? au.innerText.trim() : '匿名用户',
                headline: '',
                voteup: 0,
                comment: 0,
                blocks: bs
              });
            }
          }

          res.answers.sort(function(x, y){ return y.voteup - x.voteup; });

          // 页面内调接口取整页回答（带 Cookie 和同源身份，比页面内嵌的 4~5 条多）
          try {
            var qid = (location.pathname.match(/question\/(\d+)/) || [])[1];
            if (qid) {
              var inc = 'data[*].content,voteup_count,comment_count,author,is_collapsed';
              var api = '/api/v4/questions/' + qid + '/answers?include=' +
                        encodeURIComponent(inc) + '&limit=' + LIMIT +
                        '&offset=' + OFFSET + '&sort_by=default';
              var r = await fetch(api, {
                credentials: 'include',
                headers: { 'x-requested-with': 'fetch' }
              });
              NgaBridge.onLog('answers api status=' + r.status);
              if (r.ok) {
                var j = await r.json();
                var list = j.data || [];
                NgaBridge.onLog('answers api returned=' + list.length +
                                ' total=' + (j.paging ? j.paging.totals : '?') +
                                ' isEnd=' + (j.paging ? j.paging.is_end : '?'));
                if (list.length) {
                  // 接口数据比页面内嵌的全，直接替换
                  res.answers = list.map(function(a){
                    return {
                      id: String(a.id || ''),
                      author: (a.author && a.author.name) || '匿名用户',
                      headline: (a.author && a.author.headline) || '',
                      voteup: a.voteup_count || 0,
                      comment: a.comment_count || 0,
                      blocks: toBlocks(a.content || a.excerpt || '')
                    };
                  }).filter(function(a){ return a.blocks.length > 0; });
                }
                res.total = (j.paging && j.paging.totals) || 0;
                res.isEnd = !!(j.paging && j.paging.is_end);
              }
            }
          } catch(e) { NgaBridge.onLog('answers api err: ' + e); }

          try { NgaBridge.onResult(JSON.stringify(res)); } catch(e) {}
        })();
    """
}

/** 抓取结果：问题描述 + 回答列表 */
data class ZhihuAnswerResult(
    val detailBlocks: List<ZhihuBlock>,
    val answers: List<ZhihuAnswer>,
    /** 回答总数，接口没给则为 0 */
    val total: Int = 0,
    /** 是否已到最后一页 */
    val isEnd: Boolean = false
)

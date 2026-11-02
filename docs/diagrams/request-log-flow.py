"""Sơ đồ tuần tự: một request mang request id (MDC) qua gateway → analysis-service → thread của job.
Mỗi cột là một thành phần, mỗi mũi tên là một lần gọi, đọc từ trên xuống theo thời gian.
Sinh bằng skill arch-diagram (svgkit). Chạy: python3 docs/diagrams/request-log-flow.py
"""
import os, sys
sys.path.insert(0, os.path.expanduser("~/.claude/skills/arch-diagram"))
import svgkit as k

GREY, ORANGE = "#8f897d", "#b5761c"
W = 1860
COLS = {  # tên: (tâm x, tiêu đề, phụ đề, tầng)
    "app": (95, "App BaoFit", "điện thoại", k.APP),
    "gw": (285, "Gateway", "RequestLogFilter", k.API),
    "tc": (480, "Tomcat", "+ LogContext.RequestId", k.STR),
    "sec": (665, "Spring Security", "kiểm token", k.IFC),
    "uid": (845, "LogContext.UserId", "filter", k.STR),
    "svc": (1030, "Controller → Service", "AnalysisService.submit", k.SVC),
    "job": (1240, "Thread của job", "CopyToJobThreads", k.ADP),
}
MDC_X = 1490


def x(c):
    return COLS[c][0]


def raw(s):
    k._o.append(s)


def text(x0, y, t, size=10.5, color="#6f6b62", mono=False, anchor="start", bold=False):
    fam = f' font-family="{k.M}"' if mono else ""
    raw(f'<text x="{x0}" y="{y}" font-size="{size}"{fam} fill="{color}" text-anchor="{anchor}"'
        f'{" font-weight=\"700\"" if bold else ""} xml:space="preserve">{k.esc(t)}</text>')


def msg(a, b, y, label, sub=None, dashed=False, color=GREY):
    x1, x2 = x(a), x(b)
    d = ' stroke-dasharray="6 4"' if dashed else ""
    tip = x2 - 9 if x2 > x1 else x2 + 9
    raw(f'<line x1="{x1}" y1="{y}" x2="{tip}" y2="{y}" stroke="{color}" stroke-width="1.8"{d}/>')
    s = 1 if x2 > x1 else -1
    raw(f'<polygon points="{x2},{y} {x2-9*s},{y-5.5} {x2-9*s},{y+5.5}" fill="{color}"/>')
    text((x1 + x2) / 2, y - 7, label, 10.8, "#3a3a3a", anchor="middle")
    if sub:
        text((x1 + x2) / 2, y + 15, sub, 10, "#8d8a80", anchor="middle")


def note(c, y, lines, prose=()):
    """Việc một thành phần tự làm (gọi hàm của chính nó): hộp nhỏ bám vào cột của nó."""
    col, bg = COLS[c][3]
    w = max([len(l) * 6.72 for l in lines] + [len(p) * 5.6 for p in prose]) + 24
    h = 16 * (len(lines) + len(prose)) + 12
    raw(f'<rect x="{x(c)-12}" y="{y}" width="{w}" height="{h}" rx="7" fill="{bg}" stroke="{col}" stroke-width="1.4"/>')
    yy = y + 20
    for l in lines:
        text(x(c), yy, l, 11.2, "#2f2f2f", mono=True); yy += 16
    for p in prose:
        text(x(c), yy, p, 10.5); yy += 16
    return y + h


def mdc(y, thread, value, color="#2f2f2f"):
    text(MDC_X, y, thread, 10.5, "#8d8a80")
    text(MDC_X + 112, y, value, 11, color, mono=True)


k.init("Một request đi qua hệ thống: ai gọi ai, theo thứ tự nào", W)
text(60, 58, "Mũi tên liền: gọi đi · mũi tên đứt: trả về · hộp nhỏ: thành phần đó tự làm · cột phải: trong túi áo (MDC) của thread đang chạy có gì",
     10.5, "#8d8a80")

BACK = len(k._o)  # khung tiến trình và đường đời chèn vào đây lúc cuối, để nằm dưới mọi thứ
for x0, label in ((202, "gateway-service · 1 tiến trình Java"), (404, "analysis-service · 1 tiến trình Java")):
    text(x0, 94, label, 10.5, "#9a9488", bold=True)
text(MDC_X, 94, "TÚI ÁO (MDC) SAU BƯỚC ĐÓ", 10, "#9a9488", bold=True)

for c, (cx, title, sub, (col, bg)) in COLS.items():
    raw(f'<rect x="{cx-86}" y="104" width="172" height="48" rx="9" fill="{bg}" stroke="{col}" stroke-width="1.8"/>')
    text(cx, 124, title, 12.5, col, anchor="middle", bold=True)
    text(cx, 141, sub, 10, "#8d8a80", anchor="middle")

y = 190
msg("app", "gw", y, "① POST /api/v1/analysis", "ảnh + token, qua Cloudflare → nginx")
mdc(y, "", "")
y += 34
y = note("gw", y, ["② id = UUID.randomUUID()   // 7f3a…", "   MDC.put(\"requestId\", id)"])
mdc(y - 14, "gateway · exec-1", "{ requestId=7f3a }")
y += 40
msg("gw", "tc", y, "③ chain.doFilter(…)  →  HTTP", "header X-Request-Id: 7f3a")
y += 34
y = note("tc", y, ["④ RequestId.requestInitialized()", "   MDC.put(\"requestId\", header)"],
         ["   chạy trước mọi filter"])
mdc(y - 30, "analysis · exec-3", "{ requestId=7f3a }")
y += 40
msg("tc", "sec", y, "⑤ doFilter()", "log: [req=7f3a user=-] Securing POST …")
y += 34
y = note("sec", y, ["⑥ kiểm chữ ký, hạn dùng, uid"])
y += 40
msg("sec", "uid", y, "⑦ doFilter()", "token hợp lệ")
y += 34
y = note("uid", y, ["⑧ MDC.put(\"userId\", uid)"])
mdc(y - 14, "analysis · exec-3", "{ requestId=7f3a, userId=1111 }")
y += 40
msg("uid", "svc", y, "⑨ submit(ảnh, …)", "qua DispatcherServlet")
y += 34
y = note("svc", y, ["⑩ jobs.save(…)", "   executor.execute(job)"])
yGive = y + 26
msg("svc", "job", yGive, "⑪ decorate(job)", "chụp MDC: { requestId, userId }", color=ORANGE)
y = yGive + 46
msg("svc", "tc", y, "⑫ return 202", "đi ngược qua UserId, Spring Security", dashed=True)
y += 34
y = note("tc", y, ["⑬ RequestId.requestDestroyed()", "   MDC.clear()"],
         ["   sau cả lúc Tomcat ghi stack trace (nếu có lỗi)"])
mdc(y - 30, "analysis · exec-3", "{ }   thread về hàng chờ", "#8d8a80")
y += 40
msg("tc", "gw", y, "⑭ response 202", dashed=True)
y += 26
y = note("gw", y, ["⑮ log.info(…)   MDC.remove(\"requestId\")"],
         ["   log: [req=7f3a] POST /api/v1/analysis -> analysis … 202 in 85 ms"])
mdc(y - 30, "gateway · exec-1", "{ }", "#8d8a80")
y += 40
msg("gw", "app", y, "⑯ 202", "header X-Request-Id: 7f3a", dashed=True)

# thread của job: chạy song song từ ⑪, vẽ bên dưới cho khỏi chồng lên luồng chính
yj = y + 60
text(x("job") - 14, yj + 18, "song song, trên thread task-1 →", 10.5, ORANGE, anchor="end", bold=True)
yj = note("job", yj, ["⑰ MDC.setContextMap(chụp)"])
mdc(yj - 14, "analysis · task-1", "{ requestId=7f3a, userId=1111 }", ORANGE)
yj = note("job", yj + 14, ["⑱ process(jobId)"], ["   gọi model, lưu kết quả", "   log: [req=7f3a user=1111]", "   Analysis …: 3 dishes"])
yj = note("job", yj + 14, ["⑲ MDC.clear()   finally"], ["   thường xong sau ⑯"])
mdc(yj - 14, "analysis · task-1", "{ }", "#8d8a80")

end = yj + 30
back = [f'<rect x="{x0}" y="76" width="{x1-x0}" height="{end-76}" rx="12" fill="none" stroke="#c9c3b6" '
        f'stroke-width="1.3" stroke-dasharray="7 5"/>' for x0, x1 in ((190, 382), (392, 1470))]
back += [f'<line x1="{cx}" y1="152" x2="{cx}" y2="{end-14}" stroke="{col}" stroke-width="1.2" opacity=".45" '
         f'stroke-dasharray="3 4"/>' for cx, _, _, (col, _) in COLS.values()]
back.append(f'<rect x="{x("job")-5}" y="{yGive+8}" width="10" height="{yj-yGive-8}" rx="3" fill="#fdf1dc" '
            f'stroke="{ORANGE}" stroke-width="1.2"/>')
k._o[BACK:BACK] = back
k._maxy = end
k.save(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "request-log-flow.svg"))

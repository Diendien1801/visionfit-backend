"""Sơ đồ CI/CD của VisionFit: push lên main → GitHub Actions → GHCR + AWS SSM → EC2.
Sinh bằng skill arch-diagram (svgkit). Chạy: python3 docs/diagrams/cicd-flow.py
"""
import os, sys
sys.path.insert(0, os.path.expanduser("~/.claude/skills/arch-diagram"))
import svgkit as k

GREY = "#9c968a"


def head(x, y, d):
    """Đầu mũi tên, đỉnh tại (x, y), hướng d: up / left / right (down dùng k.ar)."""
    pts = {"up": f"{x},{y} {x-5.5},{y+9} {x+5.5},{y+9}",
           "left": f"{x},{y} {x+9},{y-5.5} {x+9},{y+5.5}",
           "right": f"{x},{y} {x-9},{y-5.5} {x-9},{y+5.5}"}[d]
    k._o.append(f'<polygon points="{pts}" fill="{GREY}"/>')


def dash(x1, y1, x2, y2):
    k._o.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{GREY}" '
                f'stroke-width="1.7" stroke-dasharray="5 4"/>')


def lt(x, y, t):
    """Nhãn căn trái, đặt cạnh một mũi tên dọc."""
    k._o.append(f'<text x="{x}" y="{y}" font-size="10.5" fill="#6f6b62">{k.esc(t)}</text>')


def line_y(y, lines, i):
    """Toạ độ y (baseline) của dòng thứ i trong một hộp có sub và role."""
    return y + 75 + sum(k._hgt(l.partition("||")[0]) for l in lines[:i])


k.init("VisionFit · push lên main là tự deploy (CI/CD qua AWS SSM)")
k.legend([("bạn", k.APP), ("GitHub", k.API), ("AWS SSM", k.STR), ("server EC2", k.SVC),
          ("IAM · thẻ và quyền", k.IFC), ("kho image", k.ADP)], markers=False)
k._o.append('<text x="1040" y="82" font-size="10.5" fill="#6b6b6b">quyền đã có</text>')
k.badge(1118, 82, "✓")
k._o.append('<text x="1140" y="82" font-size="10.5" fill="#6b6b6b">quyền thêm hôm nay</text>')
k.badge(1258, 82, "+")

# ---- bạn -------------------------------------------------------------------
y = k.box(430, 104, 480, "Bạn · Mac", "", ["git push origin main"], k.APP,
          role="Việc duy nhất còn làm bằng tay.")
k.ln(670, y, 670, y + 42); k.ar(670, y + 48); lt(680, y + 28, "① push lên main")
yB = y + 50

# ---- hàng GitHub: kho image · nhà máy · két ----------------------------------
gh = ["##MỌI NHÁNH", "--",
      "checkout · setup-java 21||máy sạch, cài sẵn Java",
      "./gradlew test bootJar||② 39 test; đỏ thì dừng ở đây", "",
      "##CHỈ NHÁNH MAIN", "--",
      "Push images||③ build 5 image ARM, đẩy lên GHCR",
      "Deploy||④ gửi phiếu · ⑩ chờ kết quả, in log"]
yGh = k.box(430, yB, 480, "GitHub Actions · job build", "runner ubuntu-24.04-arm · .github/workflows/ci.yml",
            gh, k.API, "MỚI", role="Nhà máy: test, đóng gói image, rồi bảo server deploy.", split=220)
yGhcr = k.box(30, yB, 310, "GHCR · ghcr.io", "kho image của GitHub, public",
              ["diendien1801/visionfit-<service>", "    :<7 ký tự commit>", "    :latest", "",
               "· 5 image cho mỗi commit trên main", "· server kéo về không cần đăng nhập",
               "· quay về bản cũ = kéo lại tag cũ"],
              k.ADP, "MỚI", role="Kho: giữ mọi bản image theo tag.")
k.box(1000, yB, 310, "GitHub Secrets", "repo → Settings → Secrets → Actions",
      ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "· chỉ bước Deploy đọc, qua env",
       "· không nằm trong repo, không hiện trong log"],
      k.API, "MỚI", role="Két: giữ chìa của thẻ 1.")
yPush = line_y(yB, gh, 7) - 4
k.ln(430, yPush, 349, yPush); head(340, yPush, "left"); k.lb(385, yPush - 8, "③ push")
k.ln(1000, yB + 120, 919, yB + 120); head(910, yB + 120, "left"); k.lb(955, yB + 112, "thẻ 1")

# ---- hàng AWS: trụ sở SSM · quầy IAM ----------------------------------------
yC = max(yGh, yGhcr) + 64
k.ln(600, yGh, 600, yC - 6); k.ar(600, yC); lt(608, yGh + 36, "④ gửi phiếu, trình thẻ 1")
k.ln(760, yC, 760, yGh + 9); head(760, yGh, "up"); lt(768, yGh + 36, "⑩ hỏi kết quả mỗi 5 giây")
ySsm = k.box(430, yC, 480, "AWS Systems Manager (SSM)", "region Sydney · ap-southeast-2",
             ["##PHIẾU VIỆC · document VisionfitDeploy", "--",
              "sudo -iu ubuntu .../deploy.sh||việc duy nhất được giao",
              "· nội dung nằm ở infra/ec2/ssm-deploy.yml", "",
              "##MỘT LẦN GỬI PHIẾU (Run Command)", "--",
              "Pending → InProgress → Success | Failed",
              "· GitHub hỏi trạng thái này cho tới khi xong"],
             k.STR, "MỚI", role="Trụ sở: giữ phiếu, chờ agent gọi tới để giao.", split=250)
yIam = k.box(1000, yC, 310, "IAM · quầy soát thẻ", "dịch vụ Global",
             ["##THẺ 1 · IAM user github-deploy", "· GitHub cầm: access key, không hết hạn", "--",
              "+ ssm:SendCommand", "    · chỉ phiếu VisionfitDeploy", "    · chỉ máy i-09892524bb422eacd",
              "+ ssm:GetCommandInvocation", "    · đọc kết quả", "",
              "##THẺ 2 · role visionfit-backup-role", "· máy EC2 đeo: key tạm, AWS tự đổi", "--",
              "✓ s3:PutObject → bucket backup", "+ AmazonSSMManagedInstanceCore",
              "    · agent được gọi ra SSM"],
             k.IFC, "SỬA", role="Mọi lời gọi tới AWS đều phải trình thẻ.")
k.ln(910, yC + 50, 991, yC + 50); head(1000, yC + 50, "right"); k.lb(955, yC + 42, "⑤ soát thẻ")

# ---- server ------------------------------------------------------------------
yD = ySsm + 64
k.ln(600, yD, 600, ySsm + 9); head(600, ySsm, "up"); lt(608, ySsm + 36, "⑥ hỏi việc · ⑨ báo kết quả")
dash(760, ySsm, 760, yD - 6); k.ar(760, yD); lt(768, ySsm + 36, "⑦ phiếu trả về trong cuộc gọi đó")
ec2 = ["##AMAZON-SSM-AGENT · có sẵn trong Ubuntu", "--",
       "· ⑥ liên tục gọi ra SSM hỏi việc, trình thẻ 2",
       "· ⑦ nhận phiếu VisionfitDeploy, chạy lệnh trong phiếu",
       "· ⑨ báo kết quả về SSM", "",
       "##INFRA/EC2/DEPLOY.SH · chạy bằng user ubuntu", "--",
       "git pull --ff-only||lấy code main mới nhất",
       "IMAGE_TAG = 7 ký tự commit||chọn đúng bản image",
       "docker compose pull||⑧ kéo image từ GHCR",
       "docker compose up -d||khởi động lại cái đã đổi",
       "docker image prune -af||dọn bản cũ (GHCR vẫn giữ)"]
yEc2 = k.box(430, yD, 480, "EC2 · i-09892524bb422eacd", "t4g.small ARM · Ubuntu · Sydney", ec2,
             k.SVC, "SỬA", role="Chi nhánh: không mở cổng nào, tự gọi ra nhận việc.", split=250)
yPull = line_y(yD, ec2, 10) - 4
k.ln(430, yPull, 185, yPull); k.ln(185, yPull, 185, yGhcr + 9); head(185, yGhcr, "up")
k.lb(307, yPull - 8, "⑧ kéo image")

# ---- vì sao thẻ 1 là IAM user --------------------------------------------------
yE = max(yEc2, yIam) + 56
k.lb(670, yE - 16, "Vì sao thẻ 1 là IAM user mà không phải OIDC", c="#1c1c1c", s=13)
yO = k.box(30, yE, 630, "Kế hoạch a2 · OIDC", "đúng best practice, nhưng tài khoản này không cho làm",
           ["##CẦN TẠO", "--",
            "IAM → Identity providers → token.actions.githubusercontent.com",
            "IAM role github-deploy-role||trust: chỉ repo visionfit-backend, nhánh main", "",
            "##BỊ CHẶN", "--",
            "· menu IAM chỉ có Roles · Policies · IAM users · Root access management",
            "· tìm Identity providers → Feature unavailable: activate advanced features",
            "· tài khoản giao diện rút gọn khoá việc tự thêm identity provider",
            "· Activate advanced features: không đảo ngược, mất spend limit → bỏ qua"],
           k.APP, role="GitHub cấp căn cước ngắn hạn cho từng job, không key nào nằm lâu.", split=230)
yU = k.box(700, yE, 610, "Đang dùng · IAM user + access key", "IAM → IAM users",
           ["##ĐÃ LÀM", "--",
            "github-deploy||không cho đăng nhập web",
            "inline policy send-deploy||chỉ gửi đúng 1 phiếu cho đúng 1 máy",
            "Create access key||loại Third-party service",
            "GitHub Secrets ← 2 chuỗi key||copy thẳng, không qua chat", "",
            "##ĐÁNH ĐỔI", "--",
            "· key không tự hết hạn: lộ thì vào IAM xoá key, tạo key mới",
            "· nhưng người cầm key chỉ làm được một việc: deploy lại code đang ở main"],
           k.IFC, "MỚI", role="Thẻ 1 thành key vĩnh viễn, bù lại bằng quyền hẹp nhất có thể.", split=230)
k.ln(660, yE + 60, 691, yE + 60); head(700, yE + 60, "right"); k.lb(680, yE + 52, "thay")

# ---- thứ tự ----------------------------------------------------------------------
y = max(yO, yU) + 40
k.box(30, y, 1280, "Một lần push lên main, theo thứ tự", "",
      ["· ① Bạn push lên main.",
       "· ② GitHub Actions chạy 39 test trên máy sạch. Đỏ thì dừng, không deploy.",
       "· ③ Build 5 image ARM, đẩy lên GHCR với tag 7 ký tự commit.",
       "· ④ Bước Deploy lấy thẻ 1 từ GitHub Secrets, gọi SSM: gửi phiếu VisionfitDeploy cho máy i-09892524bb422eacd.",
       "· ⑤ IAM soát thẻ 1: đúng phiếu, đúng máy thì SSM nhận lệnh và để ở trạng thái Pending.",
       "· ⑥ ssm-agent trên server vẫn liên tục gọi ra SSM hỏi việc (trình thẻ 2, cũng được IAM soát).",
       "· ⑦ SSM trả phiếu ngay trong cuộc gọi đó: server không mở cổng nào, chỉ có chiều đi ra.",
       "· ⑧ deploy.sh: git pull → kéo image đúng tag từ GHCR → docker compose up -d.",
       "· ⑨ agent báo kết quả về SSM: Success hoặc Failed.",
       "· ⑩ GitHub hỏi kết quả mỗi 5 giây, in log của deploy.sh, báo xanh hoặc đỏ."],
      k.APP)

k.save(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "cicd-flow.svg"))

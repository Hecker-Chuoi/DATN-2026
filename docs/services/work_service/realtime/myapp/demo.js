// Mô phỏng từng bước: 2 người dùng (A, B) cùng mở công việc DATN-12 + Server.
// Mỗi kịch bản có nhiều chế độ (Hiện tại / Realtime làm ẩu / Đề xuất) để so sánh.
// Không phụ thuộc thư viện. Gắn vào <div class="demo" data-demo="<tên>"></div>.
(function () {
  'use strict';

  const COLORS = { Nam: '#0c66e4', Lan: '#1f845a', Minh: '#a54800' };
  const STATUS = { 'Cần làm': 'todo', 'Đang làm': 'doing', 'Chờ review': 'review', 'Hoàn thành': 'done', 'Huỷ': 'cancel' };
  const PRIO = { 'Cao': '↑', 'Trung bình': '=', 'Thấp': '↓' };
  const LABEL = { status: 'Trạng thái', assignee: 'Người thực hiện', priority: 'Độ ưu tiên', due: 'Hạn chót', desc: 'Mô tả' };

  const DESC0 = 'Cho phép đăng nhập bằng Google.';
  const DESC_A = 'Cho phép đăng nhập bằng Google + Facebook.';
  const DESC_B = 'Cho phép đăng nhập bằng Google (OAuth 2.0, bắt buộc PKCE).';
  const DESC_M = 'Cho phép đăng nhập bằng Google + Facebook (OAuth 2.0, bắt buộc PKCE).';

  const clone = (o) => JSON.parse(JSON.stringify(o));
  const esc = (s) => String(s);

  function win(name, f, v) {
    return {
      name, v, f: clone(f), pending: {}, flash: {}, by: {}, stale: {}, wrong: {}, hint: {},
      menu: null, conflict: null, editor: null, lock: '', spinner: false, toast: null,
      comments: null, reading: false, pushed: false, pill: '', draft: '', sending: false, clicks: '', net: [],
    };
  }

  function world(f, extra) {
    const s = { srv: { v: 5, f: clone(f), mark: {}, log: [] }, A: win('A', f, 5), B: win('B', f, 5) };
    if (extra) extra(s);
    return s;
  }

  function clearTransient(s) {
    for (const w of [s.A, s.B]) { w.flash = {}; w.toast = null; }
    s.srv.mark = Object.fromEntries(Object.entries(s.srv.mark).filter(([, v]) => v === 'bad'));
  }

  function srvLog(s, cls, text) {
    s.srv.log.push({ cls, text });
    if (s.srv.log.length > 4) s.srv.log.shift();
  }

  function srvSet(s, field, value, v) {
    s.srv.f[field] = value;
    s.srv.v = v;
    s.srv.mark[field] = 'good';
  }

  // ───────────────────────── Kịch bản ─────────────────────────
  const BASE = { status: 'Cần làm', assignee: 'Nam', priority: 'Trung bình', due: '20/11', desc: DESC0 };
  const COMMENTS = [{ who: 'Nam', text: 'Đã có mockup màn đăng nhập.' }, { who: 'Lan', text: 'API cần thêm trường avatar.' }];

  const DEMOS = {
    // 1 ── Hai người đổi HAI TRƯỜNG KHÁC NHAU
    fields: {
      show: ['assignee', 'priority', 'due'],
      init: () => world(BASE),
      intro: 'A và B cùng mở công việc DATN-12 (version 5). Bấm “Bước tiếp” để xem từng bước.',
      modes: {
        now: { label: 'Hiện tại', steps: [
          ['A chọn Độ ưu tiên = Cao. Server lưu → v6.', (s) => {
            s.A.f.priority = 'Cao'; s.A.v = 6; srvSet(s, 'priority', 'Cao', 6); srvLog(s, 'ok', 'A: priority=Cao ✓ v6');
          }],
          ['B không được báo gì: màn hình B vẫn là “Trung bình” (dữ liệu cũ).', (s) => { s.B.stale.priority = 1; }],
          ['B chọn Người thực hiện = Lan → v7. Response trả về cả công việc nên Độ ưu tiên trên màn B tự nhảy thành Cao, B không biết ai đổi.', (s) => {
            s.B.f.assignee = 'Lan'; s.B.f.priority = 'Cao'; s.B.stale = {}; s.B.flash.priority = 1; s.B.v = 7;
            srvSet(s, 'assignee', 'Lan', 7); srvLog(s, 'ok', 'B: assignee=Lan ✓ v7');
          }],
          ['Không mất dữ liệu (khác trường), nhưng A vẫn thấy Nam. A có thể nhắn nhầm người.', (s) => { s.A.stale.assignee = 1; }, 'warn'],
        ] },
        new: { label: 'Đề xuất', steps: [
          ['A chọn Cao → hiện NGAY (cập nhật lạc quan). Vòng xoay nhỏ = đang chờ server xác nhận.', (s) => {
            s.A.f.priority = 'Cao'; s.A.pending.priority = 1;
          }],
          ['Server: giá trị cũ A gửi kèm (Trung bình) khớp ✓ → v6. Sự kiện tới B: ô Độ ưu tiên nháy vàng và ghi “A · vừa xong”.', (s) => {
            s.A.pending = {}; s.A.v = 6; srvSet(s, 'priority', 'Cao', 6); srvLog(s, 'ok', 'A: priority=Cao (expect Trung bình) ✓ v6');
            s.B.f.priority = 'Cao'; s.B.flash.priority = 1; s.B.by.priority = 'A · vừa xong'; s.B.v = 6;
          }],
          ['B chọn Lan (gửi kèm giá trị cũ: Nam) → hiện ngay, chờ xác nhận.', (s) => {
            s.B.f.assignee = 'Lan'; s.B.pending.assignee = 1;
          }],
          ['Server ✓ → v7. A nhận sự kiện: ô Người thực hiện nháy và ghi “B · vừa xong”.', (s) => {
            s.B.pending = {}; s.B.v = 7; srvSet(s, 'assignee', 'Lan', 7); srvLog(s, 'ok', 'B: assignee=Lan (expect Nam) ✓ v7');
            s.A.f.assignee = 'Lan'; s.A.flash.assignee = 1; s.A.by.assignee = 'B · vừa xong'; s.A.v = 7;
          }, 'good:Khác trường thì cả hai đều được lưu, cả hai thấy dữ liệu mới nhất. Không popup, không nháy cả modal.'],
        ] },
      },
    },

    // 2 ── Hai người đổi CÙNG MỘT TRƯỜNG
    same: {
      show: ['status', 'assignee', 'priority'],
      init: () => world(BASE),
      intro: 'Cả A và B đang thấy Người thực hiện = Nam. Cả hai cùng muốn giao lại việc.',
      modes: {
        now: { label: 'Hiện tại', steps: [
          ['A chọn Lan, B chọn Minh gần như cùng lúc.', (s) => {
            s.A.f.assignee = 'Lan'; s.A.pending.assignee = 1; s.B.f.assignee = 'Minh'; s.B.pending.assignee = 1;
          }],
          ['Request của A tới trước → Lan, v6.', (s) => {
            s.A.pending = {}; s.A.v = 6; srvSet(s, 'assignee', 'Lan', 6); srvLog(s, 'ok', 'A: assignee=Lan ✓ v6');
          }],
          ['Request của B tới sau → ghi đè thành Minh, v7. Server không kiểm tra gì.', (s) => {
            s.B.pending = {}; s.B.v = 7; srvSet(s, 'assignee', 'Minh', 7); s.srv.mark.assignee = 'bad';
            srvLog(s, 'bad', 'B: assignee=Minh ✓ v7 (đè Lan)');
          }],
          ['A vẫn tin Lan đang làm, thực tế là Minh. Không ai được báo: ghi đè âm thầm.', (s) => {
            s.A.stale.assignee = 1;
          }, 'bad'],
        ] },
        new: { label: 'Đề xuất', steps: [
          ['A chọn Lan, B chọn Minh. Cả hai gửi kèm giá trị cũ mình đang thấy: expected = Nam.', (s) => {
            s.A.f.assignee = 'Lan'; s.A.pending.assignee = 1; s.B.f.assignee = 'Minh'; s.B.pending.assignee = 1;
          }],
          ['Server nhận A: Nam = Nam ✓ → Lan, v6. Sự kiện tới B, nhưng ô này của B đang chờ xác nhận nên B tạm giữ (quy tắc của Figma). Màn B không nhảy Minh → Lan → Minh.', (s) => {
            s.A.pending = {}; s.A.v = 6; srvSet(s, 'assignee', 'Lan', 6); srvLog(s, 'ok', 'A: assignee=Lan (expect Nam) ✓ v6');
            s.B.hint.assignee = 'Đã nhận thay đổi từ A, tạm giữ vì bạn đang gửi';
          }],
          ['Server nhận B: expected Nam ≠ hiện tại Lan → 409, kèm “A đổi lúc 10:21”. B quay về Lan và thấy hộp xung đột ngay tại ô đó.', (s) => {
            s.B.pending = {}; s.B.hint = {}; s.B.f.assignee = 'Lan'; s.B.v = 6;
            s.B.conflict = { field: 'assignee', text: '<b>A</b> vừa giao cho <b>Lan</b> (10:21). Bạn vẫn muốn giao cho <b>Minh</b>?',
              actions: [{ label: 'Vẫn giao cho Minh', pri: 1 }, { label: 'Giữ Lan' }] };
            srvLog(s, 'bad', 'B: assignee=Minh (expect Nam) ✗ 409');
          }],
          ['B chọn “Vẫn giao cho Minh” (ghi đè có chủ đích, force) → v7. A thấy ô nháy và ghi “B · Lan → Minh”.', (s) => {
            s.B.conflict = null; s.B.f.assignee = 'Minh'; s.B.v = 7;
            srvSet(s, 'assignee', 'Minh', 7); srvLog(s, 'ok', 'B: assignee=Minh (force) ✓ v7');
            s.A.f.assignee = 'Minh'; s.A.flash.assignee = 1; s.A.by.assignee = 'B · Lan → Minh'; s.A.v = 7;
          }, 'good:Vẫn có thể ghi đè, nhưng là CÓ CHỦ ĐÍCH, và người kia được biết ngay.'],
        ] },
      },
    },

    // 3 ── Menu trạng thái đang mở thì người khác đổi
    menu: {
      show: ['status', 'assignee'],
      init: () => world(BASE),
      intro: 'Trạng thái đang là “Cần làm”. Từ đây có 2 bước chuyển: Đang làm, Huỷ.',
      modes: {
        now: { label: 'Hiện tại', steps: [
          ['A mở menu Trạng thái, chuột đặt trên “Đang làm”.', (s) => {
            s.A.menu = { field: 'status', options: ['Đang làm', 'Huỷ'], hover: 'Đang làm' };
          }],
          ['B chuyển sang Đang làm → v6. A không biết.', (s) => {
            s.B.f.status = 'Đang làm'; s.B.v = 6; srvSet(s, 'status', 'Đang làm', 6); srvLog(s, 'ok', 'B: Cần làm→Đang làm ✓ v6');
            s.A.stale.status = 1;
          }],
          ['A bấm “Đang làm” → server từ chối 400. Modal chỉ hiện toast lỗi, không tải lại: A vẫn thấy “Cần làm”, bấm lại vẫn lỗi.', (s) => {
            s.A.menu = null; s.A.toast = { cls: 'err', text: 'Trạng thái hiện tại của công việc không phù hợp với bước chuyển này' };
            srvLog(s, 'bad', 'A: Cần làm→Đang làm ✗ 400');
          }, 'warn'],
        ] },
        naive: { label: 'Realtime làm ẩu', steps: [
          ['A mở menu Trạng thái, chuột đặt trên “Đang làm”.', (s) => {
            s.A.menu = { field: 'status', options: ['Đang làm', 'Huỷ'], hover: 'Đang làm' };
          }],
          ['B chuyển sang Đang làm. A nhận sự kiện và tải lại ngay: cả modal nháy thành “Đang tải…”.', (s) => {
            s.B.f.status = 'Đang làm'; s.B.v = 6; srvSet(s, 'status', 'Đang làm', 6); srvLog(s, 'ok', 'B: Cần làm→Đang làm ✓ v6');
            s.A.spinner = true;
          }],
          ['Tải xong, menu vẫn mở nhưng các lựa chọn đã ĐỔI ngay dưới con trỏ: mục đầu giờ là “Chờ review”.', (s) => {
            s.A.spinner = false; s.A.f.status = 'Đang làm'; s.A.v = 6;
            s.A.menu = { field: 'status', options: ['Chờ review', 'Huỷ'], hover: 'Chờ review' };
          }],
          ['A bấm đúng chỗ cũ → chuyển NHẦM sang “Chờ review”. Server chấp nhận vì bước chuyển hợp lệ.', (s) => {
            s.A.menu = null; s.A.f.status = 'Chờ review'; s.A.v = 7; s.A.wrong.status = 1;
            srvSet(s, 'status', 'Chờ review', 7); s.srv.mark.status = 'bad'; srvLog(s, 'bad', 'A: Đang làm→Chờ review ✓ v7 (nhầm)');
          }, 'bad'],
        ] },
        new: { label: 'Đề xuất', steps: [
          ['A mở menu Trạng thái, chuột đặt trên “Đang làm”.', (s) => {
            s.A.menu = { field: 'status', options: ['Đang làm', 'Huỷ'], hover: 'Đang làm' };
          }],
          ['B chuyển sang Đang làm. A đang mở menu nên KHÔNG thay lựa chọn dưới con trỏ: chỉ thêm một dòng báo và khoá các mục đã cũ.', (s) => {
            s.B.f.status = 'Đang làm'; s.B.v = 6; srvSet(s, 'status', 'Đang làm', 6); srvLog(s, 'ok', 'B: Cần làm→Đang làm ✓ v6');
            s.A.menu = { field: 'status', options: ['Đang làm', 'Huỷ'], stale: true, refresh: true,
              hint: '<b>B</b> vừa chuyển sang <b>Đang làm</b>' };
          }],
          ['A đóng menu (hoặc bấm “Cập nhật lựa chọn”). Lúc này mới áp dữ liệu mới: ô Trạng thái nháy và ghi “B · vừa xong”.', (s) => {
            s.A.menu = null; s.A.f.status = 'Đang làm'; s.A.flash.status = 1; s.A.by.status = 'B · vừa xong'; s.A.v = 6;
          }],
          ['Mở lại menu: lựa chọn khớp với trạng thái mới. Nếu vẫn có request cũ lọt qua, server kiểm expectedStatusId và trả 409 kèm lý do, không chuyển nhầm.', (s) => {
            s.A.menu = { field: 'status', options: ['Chờ review', 'Huỷ'] };
          }, 'good:Không có gì dịch chuyển dưới tay người dùng, không bấm nhầm, và luôn biết ai đã đổi gì.'],
        ] },
      },
    },

    // 4 ── Đang sửa mô tả thì người khác lưu
    editor: {
      show: ['status'], desc: true,
      init: () => world(BASE),
      intro: 'Mô tả hiện tại: “' + DESC0 + '”',
      modes: {
        now: { label: 'Hiện tại', steps: [
          ['A bấm sửa Mô tả và gõ thêm “+ Facebook”.', (s) => { s.A.editor = { open: true, text: DESC_A }; }],
          ['B (không biết A đang sửa) sửa thành “… (OAuth 2.0, bắt buộc PKCE)” rồi lưu → v6.', (s) => {
            s.B.f.desc = DESC_B; s.B.v = 6; srvSet(s, 'desc', DESC_B, 6); srvLog(s, 'ok', 'B: desc ✓ v6');
          }],
          ['A bấm Lưu → server ghi đè, v7. Phần “OAuth 2.0, PKCE” của B MẤT mà không ai biết.', (s) => {
            s.A.editor = null; s.A.f.desc = DESC_A; s.A.v = 7;
            srvSet(s, 'desc', DESC_A, 7); s.srv.mark.desc = 'bad'; srvLog(s, 'bad', 'A: desc ✓ v7 (xoá mất bản của B)');
          }, 'bad'],
        ] },
        naive: { label: 'Realtime làm ẩu', steps: [
          ['A bấm sửa Mô tả và gõ thêm “+ Facebook”.', (s) => { s.A.editor = { open: true, text: DESC_A }; }],
          ['B lưu bản của mình → v6. A nhận sự kiện và gọi lại fetchDetail(): modal nháy “Đang tải…”.', (s) => {
            s.B.f.desc = DESC_B; s.B.v = 6; srvSet(s, 'desc', DESC_B, 6); srvLog(s, 'ok', 'B: desc ✓ v6');
            s.A.spinner = true;
          }],
          ['fetchDetail() nạp lại ô đang gõ (setDescriptionDraft): chữ “+ Facebook” A vừa gõ BIẾN MẤT.', (s) => {
            s.A.spinner = false; s.A.v = 6; s.A.editor = { open: true, text: DESC_B, lost: true, note: 'Nội dung bạn đang gõ đã bị thay bằng bản từ server' };
          }, 'bad'],
        ] },
        new: { label: 'Đề xuất', steps: [
          ['A bấm sửa. B thấy ngay nhãn “A đang sửa mô tả…” (presence) nên có thể chờ.', (s) => {
            s.A.editor = { open: true, text: DESC_A }; s.B.lock = 'A đang sửa mô tả…';
          }],
          ['B vẫn sửa và lưu → v6. Ô A đang gõ KHÔNG bị đụng tới, chỉ hiện một dải báo phía trên.', (s) => {
            s.B.f.desc = DESC_B; s.B.v = 6; srvSet(s, 'desc', DESC_B, 6); srvLog(s, 'ok', 'B: desc (expect bản gốc) ✓ v6');
            s.A.editor.banner = '<b>B</b> vừa lưu mô tả (10:22) · <u>Xem bản của B</u>';
          }],
          ['A bấm Lưu, gửi kèm bản gốc lúc bắt đầu sửa → 409. A thấy hai bản cạnh nhau, không mất chữ nào.', (s) => {
            srvLog(s, 'bad', 'A: desc (expect bản gốc) ✗ 409'); s.A.v = 6;
            s.A.editor = { open: true, compare: { theirs: DESC_B, mine: DESC_A },
              actions: [{ label: 'Gộp & lưu', pri: 1 }, { label: 'Dùng bản của B' }, { label: 'Sao chép bản của tôi' }] };
          }],
          ['A gộp hai bản rồi lưu (force) → v7. B thấy ô Mô tả nháy và ghi “A · vừa xong”.', (s) => {
            s.A.editor = null; s.A.f.desc = DESC_M; s.A.v = 7; s.B.lock = '';
            srvSet(s, 'desc', DESC_M, 7); srvLog(s, 'ok', 'A: desc (force) ✓ v7');
            s.B.f.desc = DESC_M; s.B.flash.desc = 1; s.B.by.desc = 'A · vừa xong'; s.B.v = 7;
          }, 'good:Không bao giờ làm mất chữ đang gõ. Xung đột được phát hiện, và người dùng tự quyết.'],
        ] },
      },
    },

    // 5 ── Bình luận: bấm đúp + đang đọc giữa danh sách
    comment: {
      show: [], comments: true,
      init: () => world(BASE, (s) => {
        s.A.comments = clone(COMMENTS); s.B.comments = clone(COMMENTS); s.B.reading = true;
        s.A.draft = 'Mình nhận phần API nhé';
      }),
      intro: 'A sắp gửi bình luận. B đang đọc giữa danh sách bình luận.',
      modes: {
        now: { label: 'Hiện tại', steps: [
          ['Mạng chậm, A bấm Gửi 2 lần. Nút không khoá khi đang gửi.', (s) => { s.A.clicks = '👆 ×2'; }],
          ['Server nhận 2 request → tạo 2 bình luận giống hệt nhau.', (s) => {
            const c = { who: 'A', text: s.A.draft }; s.A.comments.push(c, Object.assign({ dup: 1 }, c)); s.A.draft = ''; s.A.clicks = '';
            srvLog(s, 'ok', 'A: POST comment ✓ #3'); srvLog(s, 'bad', 'A: POST comment ✓ #4 (trùng)');
          }],
          ['Tối đa 5 giây sau (polling), danh sách của B dài thêm và đoạn B đang đọc bị đẩy đi.', (s) => {
            const c = { who: 'A', text: 'Mình nhận phần API nhé' }; s.B.comments.push(c, Object.assign({ dup: 1 }, c)); s.B.pushed = true;
          }, 'bad'],
        ] },
        new: { label: 'Đề xuất', steps: [
          ['A bấm Gửi. Nút khoá ngay thành “Đang gửi…”. Request mang Idempotency-Key = k1.', (s) => { s.A.sending = true; }],
          ['Mạng chập chờn, request được gửi lại với CÙNG k1 → server trả lại kết quả cũ, không tạo thêm.', (s) => {
            s.A.sending = false; s.A.comments.push({ who: 'A', text: s.A.draft }); s.A.draft = '';
            srvLog(s, 'ok', 'A: POST comment [k1] ✓ #3'); srvLog(s, 'info', 'A: POST comment [k1] ↩ trả lại #3');
          }],
          ['B đang đọc giữa danh sách nên không bị đẩy đi. Chỉ hiện nút “1 bình luận mới ↓”.', (s) => { s.B.pill = '1 bình luận mới ↓'; }],
          ['B bấm nút → cuộn xuống, bình luận mới nháy nhẹ.', (s) => {
            s.B.pill = ''; s.B.reading = false; s.B.comments.push({ who: 'A', text: 'Mình nhận phần API nhé', isNew: 1 });
          }, 'good:Mỗi lần bấm chỉ tạo đúng một bình luận. Người đang đọc không bị mất chỗ.'],
        ] },
      },
    },

    // 6 ── Kết quả trả về sai thứ tự
    order: {
      show: ['assignee', 'priority'], net: true,
      init: () => world(BASE),
      intro: 'Hai request của A chạy song song và có thể trả về sai thứ tự.',
      modes: {
        naive: { label: 'Realtime chưa so version', steps: [
          ['B đổi Người thực hiện = Lan → v6. A nhận sự kiện và bắt đầu tải lại (R1).', (s) => {
            s.B.f.assignee = 'Lan'; s.B.v = 6; srvSet(s, 'assignee', 'Lan', 6); srvLog(s, 'ok', 'B: assignee=Lan ✓ v6');
            s.A.net = ['R1 GET item … đang chờ'];
          }],
          ['Ngay lúc đó A chọn Độ ưu tiên = Cao (R2) → server lưu v7. R2 về trước: A thấy Cao, v7.', (s) => {
            srvSet(s, 'priority', 'Cao', 7); srvLog(s, 'ok', 'A: priority=Cao ✓ v7');
            s.A.f.priority = 'Cao'; s.A.f.assignee = 'Lan'; s.A.v = 7; s.A.net = ['R1 GET item … đang chờ', 'R2 PATCH priority ✓ v7'];
          }],
          ['R1 (đọc lúc v6, khi Độ ưu tiên còn Trung bình) về SAU và ghi đè màn hình: Cao biến thành Trung bình!', (s) => {
            s.A.f.priority = 'Trung bình'; s.A.v = 6; s.A.wrong.priority = 1;
            s.A.net = ['R2 PATCH priority ✓ v7', '<span class="bad">R1 về muộn, v6 → đã áp (sai)</span>'];
          }, 'bad:DB vẫn đúng (Cao) nhưng màn hình sai. A tưởng thao tác của mình bị mất và bấm lại.'],
        ] },
        new: { label: 'Đề xuất', steps: [
          ['B đổi Người thực hiện = Lan → v6. A nhận sự kiện và bắt đầu tải lại (R1).', (s) => {
            s.B.f.assignee = 'Lan'; s.B.v = 6; srvSet(s, 'assignee', 'Lan', 6); srvLog(s, 'ok', 'B: assignee=Lan ✓ v6');
            s.A.net = ['R1 GET item … đang chờ'];
          }],
          ['A chọn Cao (R2) → v7. R2 về trước: A thấy Cao, v7.', (s) => {
            srvSet(s, 'priority', 'Cao', 7); srvLog(s, 'ok', 'A: priority=Cao ✓ v7');
            s.A.f.priority = 'Cao'; s.A.f.assignee = 'Lan'; s.A.v = 7; s.A.net = ['R1 GET item … đang chờ', 'R2 PATCH priority ✓ v7'];
          }],
          ['R1 về sau với version 6, nhỏ hơn version 7 đang hiển thị → BỎ QUA. Màn hình giữ Cao.', (s) => {
            s.A.net = ['R2 PATCH priority ✓ v7', '<span class="ok">R1 về muộn, v6 &lt; v7 → bỏ qua</span>'];
          }, 'good:Chỉ một phép so sánh version là chặn được hoàn toàn lỗi “nhảy ngược”.'],
        ] },
      },
    },
  };

  // ───────────────────────── Vẽ ─────────────────────────
  function valueHtml(field, v) {
    if (field === 'status') return `<span class="chip ${STATUS[v] || 'todo'}">${esc(v)}</span>`;
    if (field === 'assignee') return `<span class="av" style="background:${COLORS[v] || '#888'}">${esc(v)[0]}</span>${esc(v)}`;
    if (field === 'priority') return `<span class="prio">${PRIO[v] || ''} ${esc(v)}</span>`;
    return esc(v);
  }

  function rowHtml(w, field) {
    const cls = ['dv', w.pending[field] && 'pending', w.stale[field] && 'stale', w.wrong[field] && 'wrong'].filter(Boolean).join(' ');
    let h = `<div class="dr ${w.flash[field] ? 'flash' : ''}"><span class="dl">${LABEL[field]}</span>` +
      `<span class="${cls}">${valueHtml(field, w.f[field])}${w.pending[field] ? '<i class="spin"></i>' : ''}</span>` +
      `<span class="dby">${w.by[field] || ''}</span></div>`;
    if (w.hint[field]) h += `<div class="dhint">${w.hint[field]}</div>`;
    if (w.menu && w.menu.field === field) {
      const m = w.menu;
      h += `<div class="dmenu">${m.hint ? `<div class="dmenu-hint">${m.hint}</div>` : ''}` +
        m.options.map((o) => `<div class="dopt ${m.hover === o ? 'hover' : ''} ${m.stale ? 'off' : ''}"><span>${o}</span>${m.hover === o ? '<span>👆</span>' : ''}</div>`).join('') +
        `${m.refresh ? '<div class="dopt link">↻ Cập nhật lựa chọn</div>' : ''}</div>`;
    }
    if (w.conflict && w.conflict.field === field) {
      h += `<div class="dconf"><div>⚠️ ${w.conflict.text}</div><div class="dbtns">` +
        w.conflict.actions.map((a) => `<span class="dbtn ${a.pri ? 'pri' : ''}">${a.label}</span>`).join('') + '</div></div>';
    }
    return h;
  }

  function editorHtml(w) {
    let h = `<div class="ded ${w.flash.desc ? 'flash' : ''}"><div class="dr" style="padding:0"><span class="dl">Mô tả</span><span></span><span class="dby">${w.by.desc || ''}</span></div>`;
    if (w.lock) h += `<div class="dlock">✎ ${w.lock}</div>`;
    const e = w.editor;
    if (e && e.banner) h += `<div class="dbanner">ℹ️ ${e.banner}</div>`;
    if (e && e.compare) {
      h += `<div class="dcmp"><div><b>Bản hiện tại (của B)</b>${e.compare.theirs}</div><div><b>Bản của bạn</b>${e.compare.mine}</div></div>` +
        `<div class="dbtns">${e.actions.map((a) => `<span class="dbtn ${a.pri ? 'pri' : ''}">${a.label}</span>`).join('')}</div>`;
    } else if (e) {
      h += `<div class="dtext editing ${e.lost ? 'lost' : ''}">${e.text}<span class="caret">|</span></div>`;
      if (e.note) h += `<div class="dnote">✗ ${e.note}</div>`;
    } else {
      h += `<div class="dtext">${w.f.desc}</div>`;
    }
    return h + '</div>';
  }

  function commentsHtml(w) {
    const list = w.comments.map((c) => `<div class="dc ${c.dup ? 'dup' : ''} ${c.isNew ? 'flash' : ''}"><b>${c.who}:</b> ${c.text}</div>`).join('');
    return `<div class="dcm"><span class="dl" style="color:var(--muted);font-size:11.5px">Bình luận</span>` +
      `<div class="dcm-list ${w.reading ? 'reading' : ''} ${w.pushed ? 'pushed' : ''}">${list}</div>` +
      `${w.pill ? `<div class="dpill">${w.pill}</div>` : ''}` +
      `<div class="dcm-in"><span>${w.draft || 'Viết bình luận…'}</span>` +
      `<span class="dbtn ${w.sending ? 'off' : 'pri'}">${w.sending ? '<i class="spin"></i> Đang gửi…' : 'Gửi'}</span>` +
      `${w.clicks ? `<span class="dclick">${w.clicks}</span>` : ''}</div></div>`;
  }

  function winHtml(w, def, label) {
    let body;
    if (w.spinner) {
      body = '<div class="dw-spinner"><i class="spin"></i> Đang tải lại…</div>';
    } else {
      body = `<div class="dw-title"><span class="code">DATN-12</span> Đăng nhập mạng xã hội</div>` +
        def.show.map((f) => rowHtml(w, f)).join('') +
        (def.desc ? editorHtml(w) : '') +
        (def.comments ? commentsHtml(w) : '') +
        (def.net && w.net.length ? `<div class="dnet">${w.net.map((n) => `<div>${n}</div>`).join('')}</div>` : '');
    }
    const toast = w.toast ? `<div class="dtoast ${w.toast.cls}">${w.toast.text}</div>` : '';
    return `<div class="dw"><div class="dw-bar"><i></i><i></i><i></i><b>${label}</b><span class="ver">đang thấy v${w.v}</span></div>` +
      `<div class="dw-body">${body}</div>${toast}</div>`;
  }

  function srvHtml(srv, def) {
    const fields = def.comments ? [] : [...def.show, ...(def.desc ? ['desc'] : [])];
    const rows = fields.map((f) => `<tr><td>${LABEL[f]}</td><td class="${srv.mark[f] || ''}">${f === 'desc' ? srv.f[f].replace('Cho phép đăng nhập bằng ', '…') : srv.f[f]}</td></tr>`).join('');
    const log = srv.log.map((l) => `<div class="${l.cls}">${l.text}</div>`).join('') || '<div class="info">(chưa có request)</div>';
    return `<div class="dsrv"><div class="dsrv-h"><span>🗄 Server · DB</span><span>v${srv.v}</span></div>` +
      (rows ? `<table>${rows}</table>` : '') + `<div class="dsrv-log">${log}</div></div>`;
  }

  // ───────────────────────── Điều khiển ─────────────────────────
  function mount(el) {
    const def = DEMOS[el.dataset.demo];
    if (!def) return;
    const modeKeys = Object.keys(def.modes);
    const qs = new URLSearchParams(location.search);
    let mode = el.dataset.mode && def.modes[el.dataset.mode] ? el.dataset.mode : modeKeys[0];
    if (def.modes[qs.get('mode')]) mode = qs.get('mode');
    let frames = [];
    let i = 0;
    let timer = null;

    function build() {
      let s = def.init();
      frames = [{ s: clone(s), text: def.intro, verdict: '' }];
      for (const [text, fn, verdict] of def.modes[mode].steps) {
        s = clone(s);
        clearTransient(s);
        fn(s);
        frames.push({ s: clone(s), text, verdict: verdict || '' });
      }
      i = 0;
    }

    function render() {
      const f = frames[i];
      const [vcls, vtext] = (f.verdict || '').split(':');
      const tag = { good: '✅ Kết quả:', bad: '❌ Kết quả:', warn: '⚠️ Kết quả:' }[vcls] || (i === 0 ? 'Bối cảnh:' : `Bước ${i}:`);
      el.innerHTML =
        `<div class="dctl"><div class="dseg">${modeKeys.map((k) => `<button type="button" data-mode="${k}" class="${k === mode ? 'on' : ''}">${def.modes[k].label}</button>`).join('')}</div>` +
        `<span class="spacer" style="flex:1"></span>` +
        `<button type="button" class="act" data-act="reset" title="Làm lại">↺</button>` +
        `<button type="button" class="act" data-act="back" ${i === 0 ? 'disabled' : ''}>‹</button>` +
        `<span class="dstep">${i} / ${frames.length - 1}</span>` +
        `<button type="button" class="act pri" data-act="next" ${i === frames.length - 1 ? 'disabled' : ''}>Bước tiếp ›</button>` +
        `<button type="button" class="act" data-act="play">${timer ? '⏸ Dừng' : '▶ Tự chạy'}</button></div>` +
        `<div class="demo-stage">${winHtml(f.s.A, def, 'Người dùng A')}${srvHtml(f.s.srv, def)}${winHtml(f.s.B, def, 'Người dùng B')}</div>` +
        `<div class="dlog ${vcls || ''}"><span class="tag">${tag}</span>${f.text}${vtext ? ' ' + vtext : ''}</div>`;
    }

    function stop() { if (timer) { clearInterval(timer); timer = null; } }

    el.addEventListener('click', (e) => {
      const b = e.target.closest('button');
      if (!b) return;
      if (b.dataset.mode) { stop(); mode = b.dataset.mode; build(); render(); return; }
      const act = b.dataset.act;
      if (act === 'reset') { stop(); i = 0; }
      if (act === 'back') { stop(); i = Math.max(0, i - 1); }
      if (act === 'next') { stop(); i = Math.min(frames.length - 1, i + 1); }
      if (act === 'play') {
        if (timer) { stop(); } else {
          if (i === frames.length - 1) i = 0;
          timer = setInterval(() => {
            if (i >= frames.length - 1) { stop(); render(); return; }
            i += 1; render();
          }, 2600);
        }
      }
      render();
    });

    build();
    if (qs.has('step')) i = Math.min(Math.max(parseInt(qs.get('step'), 10) || 0, 0), frames.length - 1);
    render();
  }

  document.querySelectorAll('.demo[data-demo]').forEach(mount);
})();

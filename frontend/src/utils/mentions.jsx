/**
 * Nhắc tên trong bình luận.
 *
 * Nội dung lưu trên server: "@[Tên hiển thị](keycloak-sub)" (work-service MentionParser đọc định dạng này
 * để gửi thông báo D1 "nhắc đến bạn"). Ô soạn thảo chỉ hiện "@Tên hiển thị"; khi gửi đi mới mã hoá lại.
 */

const UUID = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';
const MENTION_SOURCE = `@\\[([^\\[\\]\\n]{1,100})\\]\\((${UUID})\\)`;

/** Tên hiển thị của một thành viên dự án, đã bỏ ký tự làm hỏng cú pháp nhắc tên. */
export function memberDisplayName(m) {
  const raw = m?.fullName || m?.username || m?.email || 'Người dùng';
  return String(raw).replace(/[[\]\n]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 100) || 'Người dùng';
}

/** Chuỗi đã mã hoá → { text: chuỗi hiển thị, mentions: [{ name, id }] }. */
export function decodeMentions(value) {
  const mentions = [];
  const text = String(value ?? '').replace(new RegExp(MENTION_SOURCE, 'g'), (_, name, id) => {
    mentions.push({ name, id });
    return `@${name}`;
  });
  return { text, mentions };
}

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Chuỗi hiển thị → chuỗi lưu: mỗi "@Tên" thuộc danh sách đã chọn được thay bằng "@[Tên](id)".
 * Tên dài khớp trước (tránh "@An" ăn mất "@An Nguyễn"); "@Tên" phải kết thúc ở ranh giới chữ.
 */
export function encodeMentions(text, known) {
  const entries = [...known].filter(([name]) => name).sort((a, b) => b[0].length - a[0].length);
  if (!entries.length) return text;
  const byName = new Map(entries);
  const pattern = new RegExp(`@(${entries.map(([n]) => escapeRegExp(n)).join('|')})(?![\\p{L}\\p{N}_])`, 'gu');
  return String(text ?? '').replace(pattern, (whole, name) => {
    const id = byName.get(name);
    return id ? `@[${name}](${id})` : whole;
  });
}

/** Bỏ dấu + chữ thường để tìm "@ngu" khớp "Nguyễn". */
export function foldText(s) {
  return String(s ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

/** Hiển thị nội dung bình luận: "@[An](id)" thành nhãn "@An" tô màu, còn lại giữ nguyên văn bản. */
export function renderMentions(body, myId) {
  const s = String(body ?? '');
  const re = new RegExp(MENTION_SOURCE, 'g');
  const parts = [];
  let last = 0;
  let m;
  while ((m = re.exec(s)) !== null) {
    if (m.index > last) parts.push(s.slice(last, m.index));
    const isMe = myId && m[2].toLowerCase() === String(myId).toLowerCase();
    parts.push(
      <span
        key={`${m.index}-${m[2]}`}
        title={isMe ? 'Bạn được nhắc đến' : undefined}
        style={{
          color: isMe ? '#7C2D12' : '#1D4ED8',
          background: isMe ? '#FFEDD5' : '#EFF6FF',
          borderRadius: 4,
          padding: '0 3px',
          fontWeight: 600,
        }}
      >
        @{m[1]}
      </span>,
    );
    last = m.index + m[0].length;
  }
  if (last < s.length) parts.push(s.slice(last));
  return parts.length ? parts : s;
}

# Phong cách giao diện (kiểu Linear)

Áp dụng cho **mọi** màn hình mới hoặc khi thiết kế lại màn cũ. Mẫu chuẩn để tham khảo:
`src/pages/ProjectGitHubPage.jsx` và `src/components/github/ConnectRepoModal.jsx`.

Chỉ thay **phần nội dung bên trong** màn hình. Navbar, sidebar và layout khung của hệ thống giữ nguyên, trừ khi được yêu cầu rõ.

## Nguyên tắc
1. **Ít chữ.** Mỗi dòng một ý ngắn. Không có đoạn giới thiệu, không có thẻ "tính năng nổi bật", không có tiêu đề kiểu marketing. Giải thích chi tiết đưa vào `title` (tooltip) hoặc một dòng gợi ý 12px.
2. **Một cột, đọc từ trên xuống.** Trang cài đặt hoặc chi tiết rộng tối đa `640px`, căn giữa, padding `40px 16px 96px`, các phần cách nhau `32px`.
3. **Chia phần bằng nhãn nhỏ, không dùng thẻ to.** Mỗi phần gồm nhãn 12px màu `muted`, bên dưới là một khung viền 1px chứa các dòng.
4. **Dòng (row) là đơn vị chính.** Mỗi dòng cao tối thiểu 44px. Bên trái là nội dung (`flex: 1`), bên phải là một điều khiển (nút, công tắc hoặc menu `···`). Các dòng cách nhau bằng đường kẻ 1px `divider`.
5. **Không đổ bóng, không gradient, không emoji.** Bo góc 6px cho nút và ô nhập, 8px cho khung, 10px cho modal.
6. **Thao tác ít dùng gom vào menu `···`.** Ví dụ: cấp quyền lại, đổi, ngắt kết nối, xoá. Chỉ thao tác chính của dòng mới hiện thành nút.
7. **Thao tác nguy hiểm xác nhận ngay trong dòng** (thanh nền `dangerBg` có 2 nút Huỷ và nút màu đỏ). Không dùng `window.confirm`.
8. **Bật/tắt thì lưu luôn.** Không có nút "Lưu" cho cài đặt dạng công tắc. Cập nhật giao diện trước (optimistic), lỗi thì trả về như cũ, sau đó báo `toast.success('Đã lưu', { id })`.
9. **Trạng thái hiển thị bằng chấm 6px kèm chữ 12px màu `muted`**, ví dụ "● Đã kết nối". Không dùng badge nền màu.
10. **Phần chưa dùng được thì làm mờ** (`opacity: 0.45`) và giải thích bằng một dòng, không ẩn đi. Phần chỉ Admin sửa được có ghi chú "Chỉ Admin dự án thay đổi được" kèm icon `Lock` ở bên phải nhãn phần. Phân quyền lấy từ `useProjectPermission(projectId).isAdmin`.
11. **Modal gọn.** Rộng tối đa 480px, đặt cách mép trên `12vh`, lớp phủ `rgba(20,20,26,0.32)`, không blur. Gồm header 14px/600 có nút đóng, (ô tìm kiếm), danh sách dòng, footer 12px hoặc nút hành động. Đóng được bằng Esc và bấm ra ngoài. Nếu luồng có nhiều bước thì làm từng bước trong cùng một modal, có nút ←.
12. **Trạng thái tải, rỗng và lỗi đều chỉ là một dòng chữ nhỏ** (spinner 14px + "Đang tải…"). Lỗi thì có nút "Thử lại".

## Token
```js
const C = {
  accent:   '#2563EB', // màu nhấn duy nhất: nút chính, công tắc bật, dấu chọn
  text:     '#1B1B1F',
  textSoft: '#3C3F45',
  muted:    '#6B6F76', // nhãn phần, gợi ý, meta (đạt tương phản 4.5:1 trên nền trắng)
  border:   '#E6E6EA', // viền khung, menu, modal
  divider:  '#ECECEF', // kẻ giữa các dòng
  danger:   '#B42318',
  dangerBg: '#FDF6F5',
  ok:       '#2E9E5B', // chấm trạng thái "đang hoạt động"
  idle:     '#A0A3AA', // chấm trạng thái "chưa hoạt động"
};
```
- **Nền:** trang `#FFFFFF`; dòng đang chọn hoặc hover `#F4F4F7` (đang chọn trong modal dùng `#F4F5FB`).
- **Chữ:** Inter / system-ui. Cỡ 14px (thân), 13px (nút, dòng trong modal), 12px (nhãn, gợi ý, meta). Tiêu đề trang 22px/600 với `letter-spacing: -0.01em`. Không dùng chữ IN HOA và không dùng font-weight 700.
- **Nút:** cao 32px, padding `0 14px` (chính) hoặc `0 12px` (phụ), 13px/500.
  - Nút chính: nền `accent`, chữ trắng.
  - Nút phụ: nền trắng, viền `#DCDCE1`.
  - Nút icon: 32×32, nền trong suốt, phải có `aria-label`.
- **Công tắc:** `button role="switch" aria-checked`, vùng bấm 44×32, rãnh 30×18 (bật `accent`, tắt `#D5D6DC`), núm 14px.
- **Icon:** `lucide-react`, cỡ 12–16px, màu `muted`.

## Khung trang mẫu
```jsx
<div style={{ height: '100%', overflowY: 'auto', background: '#FFFFFF', color: C.text, fontSize: 14, lineHeight: 1.45 }}>
  <div style={{ maxWidth: 640, margin: '0 auto', padding: '40px 16px 96px', display: 'flex', flexDirection: 'column', gap: 32 }}>
    <header>{/* nhãn 12px · h1 22px/600 · 1 câu mô tả màu muted */}</header>
    <Section title="Tên phần" aside={/* ghi chú quyền */}>
      <Card>
        <Row>{/* nội dung flex:1 … điều khiển */}</Row>
      </Card>
    </Section>
  </div>
</div>
```
Các thành phần `Section`, `Card`, `Row`, `StatusDot`, `PrimaryButton`, `SecondaryButton`, `Switch`, `Menu`, `ConfirmBar` đang nằm cuối `ProjectGitHubPage.jsx`. Khi màn thứ hai cần dùng, chuyển chúng sang `src/components/ui/` rồi import lại, đừng chép thêm một bản nữa.

## Khả năng truy cập
Dùng phần tử thật (`button`, `a`, `input` + `label`), không gắn `onClick` lên `div`. Nút chỉ có icon phải có `aria-label`. Menu dùng `aria-haspopup`/`aria-expanded`, `role="menu"`/`menuitem`. Danh sách chọn một dùng `role="radiogroup"`/`radio`. Vùng bấm tối thiểu 32px, dòng tối thiểu 44px.

## Cách làm khi được yêu cầu sửa giao diện
1. Đọc màn hiện tại, liệt kê các trạng thái (rỗng, chưa kết nối, đã có dữ liệu, không có quyền, đang tải, lỗi).
2. Với thay đổi bố cục lớn, đề xuất ngắn hoặc dựng mockup để duyệt trước. Sửa nhỏ thì làm thẳng.
3. Giữ nguyên logic, API và luồng dữ liệu. Chỉ thay phần trình bày, trừ khi được yêu cầu khác.
4. Chạy `npm run lint` cho file đã sửa và `npm run build`.

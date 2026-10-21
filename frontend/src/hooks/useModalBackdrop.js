import { useRef } from 'react';

/**
 * useModalBackdrop
 *
 * Hook ngăn chặn triệt để lỗi modal bị tự động đóng khi người dùng bôi đen (drag-select text)
 * từ bên trong nội dung modal và nhả chuột (mouseup) ngoài vùng nền backdrop/overlay.
 *
 * Cơ chế:
 * - Thông thường, khi bôi đen text kéo ra ngoài viền modal rồi thả chuột, trình duyệt sẽ kích hoạt
 *   sự kiện `click` lên phần tử cha chung nhỏ nhất (chính là backdrop/overlay).
 * - Hook này theo dõi sự kiện `mousedown`. Chỉ khi người dùng thực sự bấm chuột xuống TRỰC TIẾP
 *   trên backdrop (e.target === e.currentTarget) VÀ nhả chuột trên backdrop, callback `onClose`
 *   mới được kích hoạt.
 *
 * Cách dùng 1 (Modal đơn):
 *   const backdropProps = useModalBackdrop(onClose);
 *   <div className="modal-overlay" {...backdropProps}>...</div>
 *
 * Cách dùng 2 (Trang có nhiều Modal):
 *   const { handleBackdropClick } = useModalBackdrop();
 *   <div className="modal-overlay" {...handleBackdropClick(() => setModalA(false))}>...</div>
 *   <div className="modal-overlay" {...handleBackdropClick(() => setModalB(false))}>...</div>
 */
export function useModalBackdrop(defaultOnClose) {
  const isMouseDownOnBackdrop = useRef(false);

  const handleMouseDown = (e) => {
    isMouseDownOnBackdrop.current = e.target === e.currentTarget;
  };

  const handleClick = (e, callback) => {
    const cb = callback || defaultOnClose;
    if (e.target === e.currentTarget && isMouseDownOnBackdrop.current) {
      cb?.(e);
    }
    isMouseDownOnBackdrop.current = false;
  };

  return {
    onMouseDown: handleMouseDown,
    onClick: (e) => handleClick(e, defaultOnClose),
    handleBackdropClick: (callback) => ({
      onMouseDown: handleMouseDown,
      onClick: (e) => handleClick(e, callback),
    }),
  };
}

export default useModalBackdrop;

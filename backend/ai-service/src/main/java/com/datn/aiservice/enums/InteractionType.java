package com.datn.aiservice.enums;

/**
 * APPROVE_ACTION: thẻ duyệt tool WRITE; ASK_USER: câu hỏi ngược;
 * READ_RESULT: kết quả tool READ chạy cùng lượt với một tool đang chờ (để gửi đủ FunctionResponse khi tiếp tục).
 */
public enum InteractionType { APPROVE_ACTION, ASK_USER, READ_RESULT }

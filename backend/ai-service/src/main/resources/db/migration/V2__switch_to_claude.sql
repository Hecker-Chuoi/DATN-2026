-- Chuyển trợ lý AI từ Gemini sang Claude: ai_messages.content đổi từ Gemini Content.toJson() sang
-- JSON một tin theo Messages API của Claude ({"role","content":[blocks]}). Hai định dạng không tương thích,
-- nên xoá hội thoại thử nghiệm cũ. ai_messages / ai_pending_interactions bị xoá theo (ON DELETE CASCADE);
-- ai_user_memories.source_conversation_id về NULL (ON DELETE SET NULL). Bộ nhớ, cài đặt và log sử dụng được giữ.
DELETE FROM ai_conversations;

COMMENT ON COLUMN ai_messages.content IS 'JSON một tin theo Messages API của Claude, giữ nguyên thinking block + signature';

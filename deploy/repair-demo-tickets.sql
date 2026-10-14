-- One-time repair for orphan tickets left by the former duplicate-guard bug.
-- Preserve ticket content; cancel only untouched submissions without a guard or business activity.
START TRANSACTION;
CREATE TEMPORARY TABLE orphan_ticket_ids AS
SELECT t.id
FROM service_ticket t
WHERE t.status = 'SUBMITTED' AND t.version = 0 AND t.assigned_agent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM active_ticket_guard g WHERE g.ticket_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM ticket_timeline l WHERE l.ticket_id = t.id OR l.id = t.id)
  AND NOT EXISTS (SELECT 1 FROM ticket_message m WHERE m.ticket_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM ai_task a WHERE a.biz_type = 'TICKET_ANALYSIS' AND a.biz_id = t.id);
INSERT INTO ticket_timeline(id,ticket_id,event_type,actor_type,actor_id,summary,detail_json,created_at)
SELECT id,id,'CANCELLED','SYSTEM',NULL,'重复提交修复：该记录未成功进入客服队列',NULL,UTC_TIMESTAMP(3)
FROM orphan_ticket_ids;
UPDATE service_ticket t JOIN orphan_ticket_ids o ON o.id = t.id
SET t.status = 'CANCELLED', t.version = t.version + 1,
    t.resolution_code = 'DUPLICATE_SUBMISSION_REPAIRED',
    t.resolution_note = '同一订单项已有活动工单，旧版本错误保留了重复提交记录。内容保留，请使用原活动工单。',
    t.updated_at = UTC_TIMESTAMP(3);
SELECT COUNT(*) AS repaired_ticket_count FROM orphan_ticket_ids;
DROP TEMPORARY TABLE orphan_ticket_ids;
COMMIT;

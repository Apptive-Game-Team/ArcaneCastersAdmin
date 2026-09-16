package com.wordonline.admin.repository.bot;

import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotDeckCardDto;
import com.wordonline.admin.dto.bot.BotForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BotAdminRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public long allocateUserId() {
        return ((Number) entityManager.createNativeQuery("SELECT allocate_bot_user_id()")
                .getSingleResult()).longValue();
    }

    public List<BotAdminDto> findAll() {
        return findRows("");
    }

    public Optional<BotAdminDto> findByUserId(long userId) {
        return findRows(" WHERE bp.user_id = :userId", userId).stream().findFirst();
    }

    private List<BotAdminDto> findRows(String suffix, Object... userId) {
        var query = entityManager.createNativeQuery("""
                SELECT bp.user_id, bp.name, bp.tier::text, bp.thinking_time_ms,
                       bp.reaction_interval_frames, bp.counter_aggression, bp.enabled,
                       bp.hospitality, u.mmr, u.status, u.selected_deck_id, d.name
                FROM bot_personas bp
                JOIN users u ON u.id = bp.user_id
                LEFT JOIN decks d ON d.id = u.selected_deck_id
                """ + suffix + " ORDER BY bp.user_id DESC");
        if (userId.length > 0) {
            query.setParameter("userId", userId[0]);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(row -> {
            long id = ((Number) row[0]).longValue();
            return new BotAdminDto(
                    id, (String) row[1], (String) row[2], ((Number) row[3]).intValue(),
                    ((Number) row[4]).intValue(), ((Number) row[5]).doubleValue(), (Boolean) row[6],
                    (Boolean) row[7], ((Number) row[8]).shortValue(), row[9].toString(),
                    row[10] == null ? null : ((Number) row[10]).longValue(), (String) row[11], findDeckCards(id)
            );
        }).toList();
    }

    private List<BotDeckCardDto> findDeckCards(long userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT m.id, m.name::text, dc.count
                FROM users u
                JOIN deck_cards dc ON dc.deck_id = u.selected_deck_id
                JOIN magics m ON m.id = dc.magic_id
                WHERE u.id = :userId
                ORDER BY m.id
                """).setParameter("userId", userId).getResultList();
        return rows.stream().map(row -> new BotDeckCardDto(
                ((Number) row[0]).longValue(), row[1].toString(), ((Number) row[2]).intValue()
        )).toList();
    }

    public long createUser(long userId, BotForm form) {
        entityManager.createNativeQuery("""
                INSERT INTO users(id, mmr, status) VALUES (:id, :mmr, CAST(:status AS user_status))
                """).setParameter("id", userId).setParameter("mmr", form.getMmr())
                .setParameter("status", form.getStatus()).executeUpdate();
        return userId;
    }

    public long createDeck(long userId, String name) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO decks(name, user_id) VALUES (:name, :userId) RETURNING id
                """).setParameter("name", name).setParameter("userId", userId).getSingleResult()).longValue();
    }

    public void selectDeck(long userId, long deckId) {
        int updated = entityManager.createNativeQuery("""
                UPDATE users SET selected_deck_id = :deckId
                WHERE id = :userId AND EXISTS (
                    SELECT 1 FROM decks WHERE id = :deckId AND user_id = :userId
                )
                """).setParameter("userId", userId).setParameter("deckId", deckId).executeUpdate();
        if (updated != 1) throw new IllegalArgumentException("Deck is not owned by bot user");
    }

    public void createPersona(long userId, BotForm form) {
        entityManager.createNativeQuery("""
                INSERT INTO bot_personas(user_id, name, tier, thinking_time_ms,
                    reaction_interval_frames, counter_aggression, enabled, hospitality)
                VALUES (:userId, :name, CAST(:tier AS bot_tier), :thinking, :reaction, :aggression, :enabled, :hospitality)
                """).setParameter("userId", userId).setParameter("name", form.getName())
                .setParameter("tier", form.getTier()).setParameter("thinking", form.getThinkingTimeMs())
                .setParameter("reaction", form.getReactionIntervalFrames())
                .setParameter("aggression", form.getCounterAggression()).setParameter("enabled", form.isEnabled())
                .setParameter("hospitality", form.isHospitality())
                .executeUpdate();
    }

    public void update(long userId, BotForm form) {
        int personaUpdated = entityManager.createNativeQuery("""
                UPDATE bot_personas SET name=:name, tier=CAST(:tier AS bot_tier),
                    thinking_time_ms=:thinking, reaction_interval_frames=:reaction,
                    counter_aggression=:aggression, enabled=:enabled, hospitality=:hospitality,
                    updated_at=CURRENT_TIMESTAMP
                WHERE user_id=:userId
                """).setParameter("userId", userId).setParameter("name", form.getName())
                .setParameter("tier", form.getTier()).setParameter("thinking", form.getThinkingTimeMs())
                .setParameter("reaction", form.getReactionIntervalFrames())
                .setParameter("aggression", form.getCounterAggression()).setParameter("enabled", form.isEnabled())
                .setParameter("hospitality", form.isHospitality())
                .executeUpdate();
        int userUpdated = entityManager.createNativeQuery("""
                UPDATE users SET mmr=:mmr, status=CAST(:status AS user_status) WHERE id=:userId AND id < 0
                """).setParameter("userId", userId).setParameter("mmr", form.getMmr())
                .setParameter("status", form.getStatus()).executeUpdate();
        if (personaUpdated != 1 || userUpdated != 1) throw new IllegalArgumentException("Bot not found: " + userId);
    }

    public void setEnabled(long userId, boolean enabled) {
        if (entityManager.createNativeQuery("UPDATE bot_personas SET enabled=:enabled, updated_at=CURRENT_TIMESTAMP WHERE user_id=:id")
                .setParameter("enabled", enabled).setParameter("id", userId).executeUpdate() != 1) {
            throw new IllegalArgumentException("Bot not found: " + userId);
        }
    }

    public void replaceSelectedDeckCards(long userId, List<Long> magicIds, List<Integer> counts) {
        Number deckId = (Number) entityManager.createNativeQuery("""
                SELECT d.id FROM users u JOIN decks d ON d.id=u.selected_deck_id AND d.user_id=u.id
                WHERE u.id=:userId AND u.id < 0
                """).setParameter("userId", userId).getSingleResult();
        entityManager.createNativeQuery("DELETE FROM deck_cards WHERE deck_id=:deckId")
                .setParameter("deckId", deckId.longValue()).executeUpdate();
        for (int i = 0; i < magicIds.size(); i++) {
            entityManager.createNativeQuery("""
                    INSERT INTO deck_cards(deck_id, magic_id, count)
                    SELECT :deckId, id, :count FROM magics WHERE id=:magicId
                    """).setParameter("deckId", deckId.longValue()).setParameter("magicId", magicIds.get(i))
                    .setParameter("count", counts.get(i)).executeUpdate();
        }
    }

    public void updateSelectedDeckName(long userId, String deckName) {
        int updated = entityManager.createNativeQuery("""
                UPDATE decks SET name=:name
                WHERE id=(SELECT selected_deck_id FROM users WHERE id=:userId)
                  AND user_id=:userId
                """).setParameter("name", deckName).setParameter("userId", userId).executeUpdate();
        if (updated != 1) throw new IllegalArgumentException("Selected deck not found for bot: " + userId);
    }

    public void replaceSelectedDeckCardsByName(long userId, List<BotDeckCardDto> cards) {
        Number deckId = (Number) entityManager.createNativeQuery("""
                SELECT d.id FROM users u JOIN decks d ON d.id=u.selected_deck_id AND d.user_id=u.id
                WHERE u.id=:userId AND u.id < 0
                """).setParameter("userId", userId).getSingleResult();
        entityManager.createNativeQuery("DELETE FROM deck_cards WHERE deck_id=:deckId")
                .setParameter("deckId", deckId.longValue()).executeUpdate();
        for (BotDeckCardDto card : cards) {
            int inserted = entityManager.createNativeQuery("""
                    INSERT INTO deck_cards(deck_id, magic_id, count)
                    SELECT :deckId, id, :count FROM magics WHERE name::text=:magicName
                    """).setParameter("deckId", deckId.longValue()).setParameter("magicName", card.magicName())
                    .setParameter("count", card.count()).executeUpdate();
            if (inserted != 1) throw new IllegalArgumentException("Magic not found: " + card.magicName());
        }
    }

    public void delete(long userId) {
        entityManager.createNativeQuery("UPDATE users SET selected_deck_id=NULL WHERE id=:id AND id < 0")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM decks WHERE user_id=:id").setParameter("id", userId).executeUpdate();
        if (entityManager.createNativeQuery("DELETE FROM users WHERE id=:id AND id < 0")
                .setParameter("id", userId).executeUpdate() != 1) {
            throw new IllegalArgumentException("Bot not found: " + userId);
        }
    }
}

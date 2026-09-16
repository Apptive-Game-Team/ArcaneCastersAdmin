package com.wordonline.admin.service;

import com.wordonline.admin.dto.MagicDto;
import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotDeckCardDto;
import com.wordonline.admin.dto.bot.BotForm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnBean(name = "secondaryJdbcTemplate")
@RequiredArgsConstructor
@Transactional(transactionManager = "secondaryTransactionManager")
public class SecondaryBotAdminService {

    @Qualifier("secondaryJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true, transactionManager = "secondaryTransactionManager")
    public List<BotAdminDto> findAll() {
        return jdbcTemplate.query("""
                SELECT bp.user_id, bp.name, bp.tier::text, bp.thinking_time_ms,
                       bp.reaction_interval_frames, bp.counter_aggression, bp.enabled,
                       bp.hospitality, u.mmr, u.status, u.selected_deck_id, d.name AS deck_name
                FROM bot_personas bp
                JOIN users u ON u.id=bp.user_id
                LEFT JOIN decks d ON d.id=u.selected_deck_id
                ORDER BY bp.user_id DESC
                """, (rs, rowNum) -> new BotAdminDto(
                rs.getLong("user_id"), rs.getString("name"), rs.getString("tier"),
                rs.getInt("thinking_time_ms"), rs.getInt("reaction_interval_frames"),
                rs.getDouble("counter_aggression"), rs.getBoolean("enabled"),
                rs.getBoolean("hospitality"), rs.getShort("mmr"),
                rs.getString("status"), rs.getObject("selected_deck_id", Long.class),
                rs.getString("deck_name"), findDeckCards(rs.getLong("user_id"))
        ));
    }

    @Transactional(readOnly = true, transactionManager = "secondaryTransactionManager")
    public Optional<BotAdminDto> find(long userId) {
        return findAll().stream().filter(bot -> bot.userId() == userId).findFirst();
    }

    @Transactional(readOnly = true, transactionManager = "secondaryTransactionManager")
    public List<MagicDto> getMagics() {
        return jdbcTemplate.query("select id, name::text, element, access_type from magics order by id", (rs, rowNum) ->
                new MagicDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    public long allocateUserId() {
        return jdbcTemplate.queryForObject("SELECT allocate_bot_user_id()", Long.class);
    }

    public void create(long userId, BotForm form) {
        requireBotId(userId);
        jdbcTemplate.update("INSERT INTO users(id, mmr, status) VALUES (?, ?, CAST(? AS user_status))",
                userId, form.getMmr(), form.getStatus());
        Long deckId = jdbcTemplate.queryForObject(
                "INSERT INTO decks(name, user_id) VALUES (?, ?) RETURNING id",
                Long.class, form.getDeckName(), userId);
        jdbcTemplate.update("UPDATE users SET selected_deck_id=? WHERE id=?", deckId, userId);
        jdbcTemplate.update("""
                INSERT INTO bot_personas(user_id, name, tier, thinking_time_ms,
                    reaction_interval_frames, counter_aggression, enabled, hospitality)
                VALUES (?, ?, CAST(? AS bot_tier), ?, ?, ?, ?, ?)
                """, userId, form.getName(), form.getTier(), form.getThinkingTimeMs(),
                form.getReactionIntervalFrames(), form.getCounterAggression(), form.isEnabled(),
                form.isHospitality());
    }

    public void update(long userId, BotForm form) {
        requireBotId(userId);
        int persona = jdbcTemplate.update("""
                UPDATE bot_personas SET name=?, tier=CAST(? AS bot_tier), thinking_time_ms=?,
                    reaction_interval_frames=?, counter_aggression=?, enabled=?, hospitality=?,
                    updated_at=CURRENT_TIMESTAMP
                WHERE user_id=?
                """, form.getName(), form.getTier(), form.getThinkingTimeMs(),
                form.getReactionIntervalFrames(), form.getCounterAggression(), form.isEnabled(),
                form.isHospitality(), userId);
        int user = jdbcTemplate.update("UPDATE users SET mmr=?, status=CAST(? AS user_status) WHERE id=? AND id<0",
                form.getMmr(), form.getStatus(), userId);
        if (persona != 1 || user != 1) throw new IllegalArgumentException("Dev bot not found: " + userId);
    }

    public void setEnabled(long userId, boolean enabled) {
        requireBotId(userId);
        if (jdbcTemplate.update("UPDATE bot_personas SET enabled=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
                enabled, userId) != 1) throw new IllegalArgumentException("Dev bot not found: " + userId);
    }

    public void replaceDeck(long userId, String deckName, List<Long> magicIds, List<Integer> counts) {
        Long deckId = selectedDeckId(userId);
        jdbcTemplate.update("UPDATE decks SET name=? WHERE id=? AND user_id=?", deckName, deckId, userId);
        jdbcTemplate.update("DELETE FROM deck_cards WHERE deck_id=?", deckId);
        for (int i = 0; i < magicIds.size(); i++) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO deck_cards(deck_id, magic_id, count)
                    SELECT ?, id, ? FROM magics WHERE id=?
                    """, deckId, counts.get(i), magicIds.get(i));
            if (inserted != 1) throw new IllegalArgumentException("Dev magic not found: " + magicIds.get(i));
        }
    }

    public void replaceDeckByNames(long userId, String deckName, List<String> magicNames, List<Integer> counts) {
        Long deckId = selectedDeckId(userId);
        jdbcTemplate.update("UPDATE decks SET name=? WHERE id=? AND user_id=?", deckName, deckId, userId);
        jdbcTemplate.update("DELETE FROM deck_cards WHERE deck_id=?", deckId);
        for (int i = 0; i < magicNames.size(); i++) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO deck_cards(deck_id, magic_id, count)
                    SELECT ?, id, ? FROM magics WHERE name::text=?
                    """, deckId, counts.get(i), magicNames.get(i));
            if (inserted != 1) throw new IllegalArgumentException("Dev magic not found: " + magicNames.get(i));
        }
    }

    public void delete(long userId) {
        requireBotId(userId);
        jdbcTemplate.update("UPDATE users SET selected_deck_id=NULL WHERE id=?", userId);
        jdbcTemplate.update("DELETE FROM decks WHERE user_id=?", userId);
        if (jdbcTemplate.update("DELETE FROM users WHERE id=? AND id<0", userId) != 1) {
            throw new IllegalArgumentException("Dev bot not found: " + userId);
        }
    }

    public boolean exists(long userId) {
        return !jdbcTemplate.queryForList("SELECT user_id FROM bot_personas WHERE user_id=?", Long.class, userId).isEmpty();
    }

    public boolean upsert(BotAdminDto source) {
        BotForm form = toForm(source);
        boolean created = !exists(source.userId());
        if (created) create(source.userId(), form); else update(source.userId(), form);
        replaceDeckByName(source.userId(), form.getDeckName(), source.cards());
        return created;
    }

    private void replaceDeckByName(long userId, String deckName, List<BotDeckCardDto> cards) {
        Long deckId = selectedDeckId(userId);
        jdbcTemplate.update("UPDATE decks SET name=? WHERE id=? AND user_id=?", deckName, deckId, userId);
        jdbcTemplate.update("DELETE FROM deck_cards WHERE deck_id=?", deckId);
        for (BotDeckCardDto card : cards) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO deck_cards(deck_id, magic_id, count)
                    SELECT ?, id, ? FROM magics WHERE name::text=?
                    """, deckId, card.count(), card.magicName());
            if (inserted != 1) throw new IllegalArgumentException("Dev magic not found: " + card.magicName());
        }
    }

    private List<BotDeckCardDto> findDeckCards(long userId) {
        return jdbcTemplate.query("""
                SELECT m.id, m.name::text, dc.count FROM users u
                JOIN deck_cards dc ON dc.deck_id=u.selected_deck_id
                JOIN magics m ON m.id=dc.magic_id WHERE u.id=? ORDER BY m.id
                """, (rs, rowNum) -> new BotDeckCardDto(rs.getLong(1), rs.getString(2), rs.getInt(3)), userId);
    }

    private Long selectedDeckId(long userId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT d.id FROM users u JOIN decks d ON d.id=u.selected_deck_id AND d.user_id=u.id
                WHERE u.id=? AND u.id<0
                """, Long.class, userId);
        if (ids.isEmpty()) throw new IllegalArgumentException("Dev selected deck not found: " + userId);
        return ids.getFirst();
    }

    private BotForm toForm(BotAdminDto source) {
        BotForm form = new BotForm();
        form.setName(source.name()); form.setTier(source.tier());
        form.setThinkingTimeMs(source.thinkingTimeMs());
        form.setReactionIntervalFrames(source.reactionIntervalFrames());
        form.setCounterAggression(source.counterAggression()); form.setEnabled(source.enabled());
        form.setHospitality(source.hospitality());
        form.setMmr(source.mmr()); form.setStatus(source.status());
        form.setDeckName(source.selectedDeckName() == null ? "Bot Deck" : source.selectedDeckName());
        return form;
    }

    private void requireBotId(long userId) {
        if (userId >= 0) throw new IllegalArgumentException("Bot user id must be negative");
    }
}

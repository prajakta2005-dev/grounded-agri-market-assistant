package com.groundedagri.market;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class MarketplaceRepository {
    private static final String LISTING_COLUMNS = "id, crop, variety, quantity_kg, price_per_kg, location, "
            + "harvest_date, available_date, seller, status, demo_data";

    private static final RowMapper<ProduceListing> LISTING_MAPPER = (rs, rowNum) -> new ProduceListing(
            rs.getString("id"), rs.getString("crop"), rs.getString("variety"),
            rs.getBigDecimal("quantity_kg").doubleValue(), rs.getBigDecimal("price_per_kg").doubleValue(),
            rs.getString("location"), rs.getString("harvest_date"), rs.getString("available_date"),
            rs.getString("seller"), rs.getString("status"), rs.getBoolean("demo_data"));

    private final JdbcTemplate jdbcTemplate;

    public MarketplaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProduceListing> searchAvailable(String query, String location) {
        String cropQuery = query.isBlank() ? "" : "%" + query.toLowerCase(Locale.ROOT) + "%";
        String locationQuery = location.isBlank() ? "" : "%" + location.toLowerCase(Locale.ROOT) + "%";
        return jdbcTemplate.query("SELECT " + LISTING_COLUMNS + " FROM market_listing "
                        + "WHERE status = 'AVAILABLE' AND (? = '' OR lower(crop) LIKE ? OR lower(variety) LIKE ?) "
                        + "AND (? = '' OR lower(location) LIKE ?) ORDER BY lower(crop), id",
                LISTING_MAPPER, cropQuery, cropQuery, cropQuery, locationQuery, locationQuery);
    }

    public void insertListing(ProduceListing listing) {
        jdbcTemplate.update("INSERT INTO market_listing "
                        + "(id, crop, variety, quantity_kg, price_per_kg, location, harvest_date, available_date, seller, status, demo_data) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::date, ?::date, ?, ?, ?)",
                listing.id(), listing.crop(), listing.variety(), BigDecimal.valueOf(listing.quantityKg()),
                BigDecimal.valueOf(listing.pricePerKg()), listing.location(), listing.harvestDate(),
                listing.availableDate(), listing.seller(), listing.status(), listing.demoData());
    }

    public Optional<ProduceListing> findAvailableByIdForUpdate(String id) {
        List<ProduceListing> listings = jdbcTemplate.query("SELECT " + LISTING_COLUMNS + " FROM market_listing "
                        + "WHERE id = ? AND status = 'AVAILABLE' FOR UPDATE",
                LISTING_MAPPER, id);
        return listings.stream().findFirst();
    }

    public void insertOrder(PreparedOrder order) {
        jdbcTemplate.update("INSERT INTO prepared_order "
                        + "(id, listing_id, crop, quantity_kg, indicative_total, buyer, status, note) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                order.id(), order.listingId(), order.crop(), BigDecimal.valueOf(order.quantityKg()),
                BigDecimal.valueOf(order.indicativeTotal()), order.buyer(), order.status(), order.note());
    }
}

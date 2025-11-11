package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException, OsmNodeMissingFieldsException, DuplicatePosNameException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Extracts relevant information from OSM tags and validates that all required fields are present.
     *
     * @param osmNode the OSM node to convert
     * @return the converted POS object
     * @throws OsmNodeMissingFieldsException if required fields are missing
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) throws OsmNodeMissingFieldsException {
        var tags = osmNode.tags();

        // Extract name (required)
        String name = tags.get("name");
        if (name == null || name.isBlank()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Extract description (optional, use description, note, or empty string)
        String description = tags.getOrDefault("description", 
                tags.getOrDefault("note", ""));

        // Extract address fields (required)
        String street = tags.get("addr:street");
        String houseNumber = tags.get("addr:housenumber");
        String postalCodeStr = tags.get("addr:postcode");
        String city = tags.get("addr:city");

        // Validate required address fields
        if (street == null || street.isBlank() ||
            houseNumber == null || houseNumber.isBlank() ||
            postalCodeStr == null || postalCodeStr.isBlank() ||
            city == null || city.isBlank()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Parse postal code
        Integer postalCode;
        try {
            postalCode = Integer.parseInt(postalCodeStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid postal code '{}' for OSM node {}", postalCodeStr, osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Map OSM amenity/shop type to PosType
        PosType posType = mapOsmTypeToPosType(tags);
        if (posType == null) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Determine campus based on location or address
        CampusType campus = determineCampus(osmNode, city);

        return Pos.builder()
                .name(name.trim())
                .description(description.trim())
                .type(posType)
                .campus(campus)
                .street(street.trim())
                .houseNumber(houseNumber.trim())
                .postalCode(postalCode)
                .city(city.trim())
                .build();
    }

    /**
     * Maps OSM amenity/shop types to PosType enum.
     *
     * @param tags the OSM tags
     * @return the corresponding PosType, or null if no match
     */
    private PosType mapOsmTypeToPosType(@NonNull Map<String, String> tags) {
        String amenity = tags.get("amenity");
        String shop = tags.get("shop");

        // Check amenity first
        if (amenity != null) {
            return switch (amenity.toLowerCase()) {
                case "cafe", "coffee_shop" -> PosType.CAFE;
                case "cafeteria", "restaurant" -> PosType.CAFETERIA;
                case "vending_machine" -> PosType.VENDING_MACHINE;
                default -> null;
            };
        }

        // Check shop type
        if (shop != null) {
            return switch (shop.toLowerCase()) {
                case "coffee", "cafe" -> PosType.CAFE;
                case "bakery" -> PosType.BAKERY;
                default -> null;
            };
        }

        return null;
    }

    /**
     * Determines the campus type based on location or address.
     * Uses heuristics based on city name and coordinates for Heidelberg.
     *
     * @param osmNode the OSM node with location data
     * @param city the city name from address
     * @return the determined CampusType, defaults to ALTSTADT if uncertain
     */
    private CampusType determineCampus(@NonNull OsmNode osmNode, String city) {
        // If not in Heidelberg, default to ALTSTADT
        if (city == null || !city.toLowerCase().contains("heidelberg")) {
            return CampusType.ALTSTADT;
        }

        // Use coordinates if available to determine campus
        if (osmNode.latitude() != null && osmNode.longitude() != null) {
            double lat = osmNode.latitude();
            double lon = osmNode.longitude();

            // Approximate campus boundaries in Heidelberg
            // ALTSTADT: around lat 49.41, lon 8.70
            // BERGHEIM: around lat 49.40, lon 8.69
            // INF (Im Neuenheimer Feld): around lat 49.42, lon 8.68

            // Simple heuristic based on latitude and longitude
            if (lat > 49.415 && lon < 8.685) {
                return CampusType.INF;
            } else if (lat < 49.405) {
                return CampusType.BERGHEIM;
            } else {
                return CampusType.ALTSTADT;
            }
        }

        // Default to ALTSTADT if coordinates are not available
        return CampusType.ALTSTADT;
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}

package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * OSM import service.
 */
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {
    private static final String OSM_API_BASE_URL = "https://www.openstreetmap.org/api/0.6/node/";
    private final HttpClient httpClient;

    public OsmDataServiceImpl() {
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Fetching OSM node {} from OpenStreetMap API", nodeId);

        try {
            URI uri = URI.create(OSM_API_BASE_URL + nodeId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 404) {
                log.warn("OSM node {} not found (HTTP 404)", nodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }

            if (response.statusCode() != 200) {
                log.error("Failed to fetch OSM node {}: HTTP {}", nodeId, response.statusCode());
                throw new OsmNodeNotFoundException(nodeId);
            }

            return parseOsmXml(response.body(), nodeId);
        } catch (OsmNodeNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching OSM node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    /**
     * Parses OSM XML response and extracts node data.
     *
     * @param inputStream the XML input stream
     * @param nodeId the expected node ID
     * @return the parsed OsmNode
     * @throws OsmNodeNotFoundException if the node is not found in the XML
     */
    private @NonNull OsmNode parseOsmXml(@NonNull InputStream inputStream, @NonNull Long nodeId) throws OsmNodeNotFoundException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            NodeList nodeList = document.getElementsByTagName("node");
            if (nodeList.getLength() == 0) {
                throw new OsmNodeNotFoundException(nodeId);
            }

            Element nodeElement = (Element) nodeList.item(0);
            String xmlNodeId = nodeElement.getAttribute("id");
            if (!xmlNodeId.equals(String.valueOf(nodeId))) {
                log.warn("Node ID mismatch: expected {}, got {}", nodeId, xmlNodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }

            // Extract latitude and longitude
            Double latitude = null;
            Double longitude = null;
            String latStr = nodeElement.getAttribute("lat");
            String lonStr = nodeElement.getAttribute("lon");
            if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                try {
                    latitude = Double.parseDouble(latStr);
                    longitude = Double.parseDouble(lonStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid lat/lon for node {}: lat={}, lon={}", nodeId, latStr, lonStr);
                }
            }

            // Extract tags
            Map<String, String> tags = new HashMap<>();
            NodeList tagList = nodeElement.getElementsByTagName("tag");
            for (int i = 0; i < tagList.getLength(); i++) {
                Node tagNode = tagList.item(i);
                if (tagNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element tagElement = (Element) tagNode;
                    String key = tagElement.getAttribute("k");
                    String value = tagElement.getAttribute("v");
                    if (!key.isEmpty() && !value.isEmpty()) {
                        tags.put(key, value);
                    }
                }
            }

            return OsmNode.builder()
                    .nodeId(nodeId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .tags(tags)
                    .build();
        } catch (OsmNodeNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing OSM XML for node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmNodeNotFoundException(nodeId);
        }
    }
}

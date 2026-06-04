package com.jarvis.revenue;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Low-level Notion operations the template builder needs — returning real IDs (unlike the human-readable
 * connector actions) so a page/database tree can be assembled. Implemented by {@link HttpNotionApi};
 * an in-memory fake is used in tests so the builder's logic is verified without the network.
 */
public interface NotionApi {

    /** True when a notion-token is in the vault (the builder is otherwise dormant). */
    boolean available();

    /** Create a page under a parent page (with optional markdown-ish block lines). Returns the new page id. */
    String createPage(String parentPageId, String title, List<String> blocks);

    /** Create a database under a parent page with a Notion-shaped property schema. Returns the database id. */
    String createDatabase(String parentPageId, String name, ObjectNode propertiesSchema);

    /** Add a row (page) to a database with Notion-shaped property values. */
    void addRow(String databaseId, ObjectNode propertyValues);
}

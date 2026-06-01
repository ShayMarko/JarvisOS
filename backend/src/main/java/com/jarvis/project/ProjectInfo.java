package com.jarvis.project;

/**
 * A detected project on the developer's machine.
 *
 * @param name  the project folder name
 * @param path  absolute path to the project root
 * @param type  detected type (maven, gradle, node, python, rust, go, xcode, git)
 * @param ide   the macOS application configured to open this type
 */
public record ProjectInfo(String name, String path, String type, String ide) {}

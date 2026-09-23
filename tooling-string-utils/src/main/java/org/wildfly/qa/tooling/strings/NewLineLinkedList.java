package org.wildfly.qa.tooling.strings;

import java.util.LinkedList;

/**
 * Custom implementation of {@link java.util.LinkedList} which customize {@code add()} method, which adds newline at
 * the end
 */
public class NewLineLinkedList extends LinkedList<String> {

	/**
	 * Adds string with {@code System.lineSeparator()}
	 * @param string
	 * @return
	 */
	@Override
	public boolean add(String string) {
		return super.add(string.concat(System.lineSeparator()));
	}
}

/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.util;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * @author t63606
 *
 */
public class CommonUtils {

	/**
	 * {@linkplain String} of size 0, is not null.
	 */
	public static String EMPTY_STRING = "";
	public static int FIRST_INDEX = 0;

	/**
	 * private default constructor, ensure this class can't be initialized.
	 */
	private CommonUtils() {
	}

	// *******************************************************************
	// NULL Checks
	// *******************************************************************

	/**
	 * Checks for a {@code null} or empty (trimmed) {@link String}.
	 *
	 * @param stringValue the {@link String} to be checked
	 *
	 * @return {@code true} if the {@link String} is {@code null} or empty;
	 *         {@code false} otherwise.
	 *//*
		 * public static boolean isNullOrEmpty(String stringValue) { return (null ==
		 * stringValue || stringValue.trim().isEmpty()) ? true : false; }
		 */

	/**
	 * Checks the supplied object that extends {@link Collection} for null or empty
	 * and returns a boolean value.
	 * <p>
	 * Returns true if the object is null or empty (i.e. length of 0). Returns false
	 * otherwise.
	 * 
	 * @return
	 *         <ul>
	 *         <li><code>true</code> - if the <code>list</code> is null or empty.
	 *         <li><code>false</code> - if the <code>list</code> is not null or not
	 *         empty.
	 *         </ul>
	 * @param list
	 */
	public static <T extends Collection<?>> boolean isNullOrEmpty(T list) {
		if (list == null || list.isEmpty()) {
			return true;
		}

		return false;
	}

	public static <K, T extends Collection<K>> Stream<K> streamOf(T list) {
		return list == null || list.isEmpty() ? Stream.empty() : list.stream();
	}

	/**
	 * Checks the supplied array for null or empty and returns a boolean value.
	 * <p>
	 * Returns true if the object is null or empty (i.e. length of 0). Returns false
	 * otherwise.
	 * 
	 * @return
	 *         <ul>
	 *         <li><code>true</code> - if the <code>list</code> is null or empty.
	 *         <li><code>false</code> - if the <code>list</code> is not null or not
	 *         empty.
	 *         </ul>
	 * @param list
	 */
	public static <T> boolean isNullOrEmpty(T[] array) {
		if (array == null || array.length == 0) {
			return true;
		}

		return false;
	}

	/**
	 * Checks the supplied array for null or empty and returns a boolean value.
	 * <p>
	 * Returns true if the object is null or empty (i.e. length of 0). Returns false
	 * otherwise.
	 * 
	 * @return
	 *         <ul>
	 *         <li><code>true</code> - if the <code>list</code> is null or empty.
	 *         <li><code>false</code> - if the <code>list</code> is not null or not
	 *         empty.
	 *         </ul>
	 * @param list
	 */
	public static boolean isNullOrEmpty(byte[] array) {
		if (array == null || array.length == 0) {
			return true;
		}

		return false;
	}

	/**
	 * Checks the supplied object that implements {@link Map} for null or empty and
	 * returns a boolean value.
	 * <p>
	 * Returns true if the object is null or empty (i.e. length of 0). Returns false
	 * otherwise.
	 * 
	 * @return
	 *         <ul>
	 *         <li><code>true</code> - if the <code>map</code> is null or empty.
	 *         <li><code>false</code> - if the <code>map</code> is not null or not
	 *         empty.
	 *         </ul>
	 * @param map
	 */
	public static <T extends Map<?, ?>> boolean isNullOrEmpty(T map) {
		if (map == null || map.isEmpty()) {
			return true;
		}

		return false;
	}

	// *******************************************************************
	// Object Conversion
	// *******************************************************************

	/**
	 * Creates an {@link ArrayList} from a comma delimited {@link String}.
	 * <p>
	 * 
	 * Assumes no whitespace.
	 * 
	 * @param commaSeparatedString a comma delimited string with no whitespace;
	 * @return
	 */
	public static ArrayList<String> commaSeparatedStringToArrayList(String commaSeparatedString, boolean trim) {

		// Check if the string is empty
		if (Strings.isNullOrEmpty(commaSeparatedString)) {
			return new ArrayList<String>();
		}

		// remove empty/null string if its not the first
		ArrayList<String> strings = new ArrayList<String>(Arrays.asList(commaSeparatedString.split(",")));
		strings.removeAll(Arrays.asList("", null));

		if (trim) {
			ArrayList<String> trimedStrings = new ArrayList<String>();

			for (String string : strings) {
				if (!Strings.isNullOrEmpty(string))
					trimedStrings.add(string.trim());
			}

			return trimedStrings;
		}

		return strings;
	}

	/**
	 * 
	 * @param checkValue
	 * @param defaultString should be non-null
	 * @return {@code checkValue} if its not null; {@code defaultString} otherwise.
	 */
	public static String emptyOrNullToDefault(String checkValue, String defaultString) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(defaultString), "Default string cannot be null");
		if (Strings.isNullOrEmpty(checkValue)) {
			return defaultString;
		}
		return checkValue;
	}

	// *******************************************************************
	// Collection Operations
	// *******************************************************************

	/**
	 * Gets the first element in {@list} if it exists.
	 * <p>
	 * 
	 * @param list
	 * @return the first element in the list if list is not empty; null otherwise.
	 */
	public static <K, T extends Collection<K>> K getFirstElement(T list) {
		if (!isNullOrEmpty(list)) {
			return list.iterator().next();
		}
		return null;
	}

	/**
	 * Gets the first element in {@list} if it exists.
	 * <p>
	 * 
	 * @param list
	 * @return the first element in the list if list is not empty; null otherwise.
	 */
	public static <T> T getFirstElement(T[] array) {
		if (!isNullOrEmpty(array)) {
			return array[FIRST_INDEX];
		}
		return null;
	}

	/**
	 * Reads a file as string given a path
	 * <p>
	 * 
	 * @param filePath assumed to be valid
	 * @return the string content; null otherwise.
	 */
	public static String readFileAsString(String filePath) throws Exception {
		return new String(Files.readAllBytes(Paths.get(filePath)));
	}

}

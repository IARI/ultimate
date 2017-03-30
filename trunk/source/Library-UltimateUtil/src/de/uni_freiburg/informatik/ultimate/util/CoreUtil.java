/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Core.
 *
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Core grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Predicate;

/**
 *
 * @author dietsch@informatik.uni-freiburg.de
 *
 */
public class CoreUtil {

	private static final String PLATFORM_LINE_SEPARATOR = System.getProperty("line.separator");
	public static final String OS = System.getProperty("os.name");
	public static final boolean OS_IS_WINDOWS = OS.toLowerCase().indexOf("win") >= 0;

	public static String getPlatformLineSeparator() {
		return PLATFORM_LINE_SEPARATOR;
	}

	public static String getIsoUtcTimestamp() {
		final TimeZone tz = TimeZone.getTimeZone("UTC");
		// Quoted "Z" to indicate UTC, no timezone offset
		final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		df.setTimeZone(tz);
		return df.format(new Date());
	}

	public static File writeFile(final String filename, final String content) throws IOException {
		return writeFile(filename, content, false);
	}

	public static File writeFile(final String filename, final String[] content) throws IOException {
		return writeFile(filename, content, false);
	}

	public static File appendFile(final String filename, final String content) throws IOException {
		return writeFile(filename, content, true);
	}

	public static File appendFile(final String filename, final String[] content) throws IOException {
		return writeFile(filename, content, true);
	}

	private static File writeFile(final String filename, final String[] content, final boolean append)
			throws IOException {
		if (content == null || content.length == 0) {
			return null;
		}
		final File file = createFile(filename);
		final IWriterConsumer funWrite = fw -> {
			for (final String line : content) {
				fw.append(line);
				fw.append(PLATFORM_LINE_SEPARATOR);
			}
		};
		writeFile(funWrite, append, file);
		return file;
	}

	private static File writeFile(final String filename, final String content, final boolean append)
			throws IOException {
		if (content == null || content.isEmpty()) {
			return null;
		}
		final File file = createFile(filename);
		writeFile(fw -> fw.append(content), append, file);
		return file;
	}

	private static void writeFile(final IWriterConsumer funWrite, final boolean append, final File file)
			throws FileNotFoundException, UnsupportedEncodingException, IOException {
		try (FileOutputStream os = new FileOutputStream(file, append);
				Writer fw = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"))) {
			funWrite.consume(fw);
			fw.close();
		}
	}

	private static File createFile(final String filename) {
		final File file = new File(filename);
		if (!file.isDirectory()) {
			final File parentFile = file.getParentFile();
			if (parentFile != null) {
				parentFile.mkdirs();
			}
		}
		return file;
	}

	public static String readFile(final String filename) throws IOException {
		final BufferedReader br =
				new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename)), "UTF8"));
		try {

			final StringBuilder sb = new StringBuilder();
			String line = br.readLine();
			while (line != null) {
				sb.append(line);
				sb.append(PLATFORM_LINE_SEPARATOR);
				line = br.readLine();
			}
			return sb.toString();
		} finally {
			br.close();
		}
	}

	public static String readFile(final File file) throws IOException {
		return readFile(file.getAbsolutePath());
	}

	/**
	 * Returns all elements of a collection that match the check defined by predicate.
	 *
	 * @param collection
	 *            The collection you want to filter. May not be null.
	 * @param predicate
	 *            The predicate you want to use to filter said collection. May not be null.
	 * @return A new collection that only contains elements for which {@link IPredicate#check(Object)} returned true.
	 */
	public static <E> Collection<E> where(final Collection<E> collection, final Predicate<E> predicate) {
		final ArrayList<E> rtr = new ArrayList<>();
		for (final E entry : collection) {
			if (predicate.test(entry)) {
				rtr.add(entry);
			}
		}
		return rtr;
	}

	/**
	 * Returns a {@link Set} of elements that are created by applying the reducer to every element in the collection.
	 *
	 * @param collection
	 *            May not be null.
	 * @param reducer
	 *            May not be null.
	 * @return
	 */
	public static <T, E> Set<T> selectDistinct(final Collection<E> collection, final IReduce<T, E> reducer) {
		final Set<T> rtr = new HashSet<>();
		for (final E entry : collection) {
			rtr.add(reducer.reduce(entry));
		}
		return rtr;
	}

	public static <T, E> Collection<T> select(final Collection<E> collection, final IReduce<T, E> reducer) {
		final Collection<T> rtr = new ArrayList<>();
		for (final E entry : collection) {
			rtr.add(reducer.reduce(entry));
		}
		return rtr;
	}

	public static <E> Collection<E> flattenMapValuesToCollection(final Map<?, E> map) {
		final Collection<E> rtr = new ArrayList<>();
		for (final Entry<?, E> entry : map.entrySet()) {
			rtr.add(entry.getValue());
		}
		return rtr;
	}

	public static <T, E> T reduce(final Set<E> collection, final IMapReduce<T, E> reducer) {
		T lastValue = null;
		for (final E entry : collection) {
			lastValue = reducer.reduce(lastValue, entry);
		}
		return lastValue;
	}

	public static <T, E> T reduce(final Collection<E> collection, final IMapReduce<T, E> reducer) {
		T lastValue = null;
		for (final E entry : collection) {
			lastValue = reducer.reduce(lastValue, entry);
		}
		return lastValue;
	}

	/**
	 * Indents a (possibly multiline) String such that the resulting StringBuilder object contains the same String, but
	 * indented with the indentPrefix. It also converts line breaks to the system-specific line separator.
	 *
	 * @param original
	 * @param indentPrefix
	 * @param forceRemoveLastLinebreak
	 *            When true, the last linebreak will always be removed, when false, an existing last line break will be
	 *            preserved (but converted to system-specific line break)
	 * @return
	 */
	public static StringBuilder indentMultilineString(final String original, final String indentPrefix,
			final boolean forceRemoveLastLinebreak) {
		final StringBuilder sb = new StringBuilder();
		final String lineSeparator = System.getProperty("line.separator");
		final String[] splitted = original.split("\\r?\\n");

		for (final String s : splitted) {
			sb.append(indentPrefix).append(s).append(lineSeparator);
		}

		final char last = original.charAt(original.length() - 1);
		if (forceRemoveLastLinebreak || last != '\n' && last != '\r') {
			sb.replace(sb.length() - lineSeparator.length(), sb.length(), "");
		}
		return sb;
	}

	public static String getCurrentDateTimeAsString() {
		return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(Calendar.getInstance().getTime());
	}

	/**
	 * Flattens a string, i.e. removes all line breaks and replaces them with separator
	 *
	 * @param original
	 * @param separator
	 * @return
	 */
	public static StringBuilder flatten(final String original, final String separator) {
		final StringBuilder sb = new StringBuilder();
		final String[] splitted = original.split("\\r?\\n");
		for (final String s : splitted) {
			sb.append(s).append(separator);
		}
		sb.replace(sb.length() - separator.length(), sb.length(), "");
		return sb;
	}

	public static <E> Collection<E> firstN(final Collection<E> collection, final int n) {
		final ArrayList<E> rtr = new ArrayList<>(n);
		int i = 1;
		for (final E elem : collection) {
			rtr.add(elem);
			++i;
			if (n < i) {
				break;
			}
		}
		return rtr;
	}

	/**
	 * Create a copy of one or more arrays. If there are more than one array, concatenate all of them.
	 */
	@SafeVarargs
	public static <T> T[] concatAll(final T[] first, final T[]... rest) {
		int totalLength = first.length;
		for (final T[] array : rest) {
			totalLength += array.length;
		}
		final T[] result = Arrays.copyOf(first, totalLength);
		int offset = first.length;
		for (final T[] array : rest) {
			System.arraycopy(array, 0, result, offset, array.length);
			offset += array.length;
		}
		return result;
	}

	public static String convertStreamToString(final InputStream is) {
		@SuppressWarnings("resource")
		final Scanner s = new Scanner(is).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}

	/**
	 * Determines if an {@link Iterable} is sorted according to the natural comparator. The order of objects that are
	 * equal according to the natural ordering is irrelevant.
	 *
	 * @param iterable
	 *            The {@link Iterable} that should be checked.
	 * @return true if the {@link Iterable} is sorted, false otherwise.
	 */
	public static <T extends Comparable<? super T>> boolean isSorted(final Iterable<T> iterable) {
		final Iterator<T> iter = iterable.iterator();
		if (!iter.hasNext()) {
			// empty iterables are always sorted
			return true;
		}
		T last = iter.next();
		while (iter.hasNext()) {
			final T current = iter.next();
			final int cmp = last.compareTo(current);
			if (cmp > 0) {
				return false;
			}
			last = current;
		}
		return true;
	}

	/**
	 * Determines if an {@link Iterable} is sorted according to the provided {@link Comparator}omparator. The order of
	 * objects that are equal according to the comparator is irrelevant.
	 *
	 * @param iterable
	 *            The {@link Iterable} that should be checked.
	 * @param comparator
	 *            The comparator that should be used for the sorting check.
	 * @return true if the {@link Iterable} is sorted, false otherwise.
	 */
	public static <T extends Comparable<? super T>> boolean isSorted(final Iterable<T> iterable,
			final Comparator<T> comparator) {
		final Iterator<T> iter = iterable.iterator();
		if (!iter.hasNext()) {
			// empty iterables are always sorted
			return true;
		}
		T last = iter.next();
		while (iter.hasNext()) {
			final T current = iter.next();
			if (comparator.compare(last, current) >= 0) {
				return false;
			}
			last = current;
		}
		return true;
	}

	@FunctionalInterface
	public interface IReduce<T, K> {
		T reduce(K entry);
	}

	@FunctionalInterface
	public interface IMapReduce<T, K> {
		T reduce(T lastValue, K entry);
	}

	@FunctionalInterface
	private interface IWriterConsumer {
		void consume(Writer fw) throws IOException;
	}
}

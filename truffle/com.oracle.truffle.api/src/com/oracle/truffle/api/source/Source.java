/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.source;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.spi.FileTypeDetector;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.Node;

/**
 * Representation of a guest language source code unit and its contents. Sources originate in
 * several ways:
 * <ul>
 * <li><strong>Literal:</strong> An anonymous text string: not named and not indexed. These should
 * be considered value objects; equality is defined based on contents.<br>
 * See {@link Source#fromText(CharSequence, String)}</li>
 * <p>
 * <li><strong>Named Literal:</strong> A text string that can be retrieved by name as if it were a
 * file, but without any assumption that the name is related to a file path. Creating a new literal
 * with an already existing name will replace its predecessor in the index.<br>
 * See {@link Source#fromNamedText(CharSequence, String)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>File:</strong> Each file is represented as a canonical object, indexed by the
 * absolute, canonical path name of the file. File contents are <em>read lazily</em> and contents
 * optionally <em>cached</em>. <br>
 * See {@link Source#fromFileName(String)}<br>
 * See {@link Source#fromFileName(String, boolean)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>URL:</strong> Each URL source is represented as a canonical object, indexed by the
 * URL. Contents are <em>read eagerly</em> and <em>cached</em>. <br>
 * See {@link Source#fromURL(URL, String)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>Reader:</strong> Contents are <em>read eagerly</em> and treated as an anonymous
 * (non-indexed) <em>Literal</em> . <br>
 * See {@link Source#fromReader(Reader, String)}</li>
 * <p>
 * <li><strong>Sub-Source:</strong> A representation of the contents of a sub-range of another
 * {@link Source}.<br>
 * See {@link Source#subSource(Source, int, int)}<br>
 * See {@link Source#subSource(Source, int)}</li>
 * <p>
 * <li><strong>AppendableSource:</strong> Literal contents are provided by the client,
 * incrementally, after the instance is created.<br>
 * See {@link Source#fromAppendableText(String)}<br>
 * See {@link Source#fromNamedAppendableText(String)}</li>
 * </ul>
 * <p>
 * <strong>File cache:</strong>
 * <ol>
 * <li>File content caching is optional, <em>on</em> by default.</li>
 * <li>The first access to source file contents will result in the contents being read, and (if
 * enabled) cached.</li>
 * <li>If file contents have been cached, access to contents via {@link Source#getInputStream()} or
 * {@link Source#getReader()} will be provided from the cache.</li>
 * <li>Any access to file contents via the cache will result in a timestamp check and possible cache
 * reload.</li>
 * </ol>
 * <p>
 *
 * @since 0.8 or earlier
 */
public abstract class Source {
    static final Logger LOG = Logger.getLogger(Source.class.getName());

    // TODO (mlvdv) consider canonicalizing and reusing SourceSection instances
    // TODO (mlvdv) connect SourceSections into a spatial tree for fast geometric lookup

    /**
     * Index of all named sources.
     */
    private static final Map<String, WeakReference<Source>> nameToSource = new HashMap<>();

    static boolean fileCacheEnabled = true;

    private static final String NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE = "do not create sub sources from compiled code";

    private String mimeType;
    private TextMap textMap;

    /**
     * Locates an existing instance by the name under which it was indexed.
     *
     * @since 0.8 or earlier
     */
    public static Source find(String name) {
        final WeakReference<Source> nameRef = nameToSource.get(name);
        return nameRef == null ? null : nameRef.get();
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     *
     * @param fileName name
     * @param reset forces any existing {@link Source} cache to be cleared, forcing a re-read
     * @return canonical representation of the file's contents.
     * @throws IOException if the file can not be read
     * @since 0.8 or earlier
     */
    public static Source fromFileName(String fileName, boolean reset) throws IOException {

        final WeakReference<Source> nameRef = nameToSource.get(fileName);
        Source source = nameRef == null ? null : nameRef.get();
        if (source == null) {
            final File file = new File(fileName);
            if (!file.canRead()) {
                throw new IOException("Can't read file " + fileName);
            }
            final String path = file.getCanonicalPath();
            final WeakReference<Source> pathRef = nameToSource.get(path);
            source = pathRef == null ? null : pathRef.get();
            if (source == null) {
                final FileSourceImpl content = new FileSourceImpl(file, fileName, path);
                source = new Impl(content);
                nameToSource.put(path, new WeakReference<>(source));
            }
        }
        if (reset) {
            source.reset();
        }
        return source;
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     *
     * @param fileName name
     * @return canonical representation of the file's contents.
     * @throws IOException if the file can not be read
     * @since 0.8 or earlier
     */
    public static Source fromFileName(String fileName) throws IOException {
        return fromFileName(fileName, false);
    }

    /**
     * Gets the canonical representation of a source file whose contents are the responsibility of
     * the client:
     * <ul>
     * <li>If no Source exists corresponding to the provided file name, then a new Source is created
     * whose contents are those provided. It is confirmed that the file resolves to a file name, so
     * it can be indexed by canonical path. However there is no confirmation that the text supplied
     * agrees with the file's contents or even whether the file is readable.</li>
     * <li>If a Source exists corresponding to the provided file name, and that Source was created
     * originally by this method, then that Source will be returned after replacement of its
     * contents with no further confirmation.</li>
     * <li>If a Source exists corresponding to the provided file name, and that Source was not
     * created originally by this method, then an exception will be raised.</li>
     * </ul>
     *
     * @param chars textual source code already read from the file, must not be null
     * @param fileName
     * @return canonical representation of the file's contents.
     * @throws IOException if the file cannot be found, or if an existing Source not created by this
     *             method matches the file name
     * @since 0.8 or earlier
     */
    public static Source fromFileName(CharSequence chars, String fileName) throws IOException {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromFileName from compiled code");
        assert chars != null;

        final WeakReference<Source> nameRef = nameToSource.get(fileName);
        Source source = nameRef == null ? null : nameRef.get();
        if (source == null) {
            final File file = new File(fileName);
            // We are going to trust that the fileName is readable.
            final String path = file.getCanonicalPath();
            final WeakReference<Source> pathRef = nameToSource.get(path);
            source = pathRef == null ? null : pathRef.get();
            if (source == null) {
                Content content = new ClientManagedFileSourceImpl(file, fileName, path, chars);
                source = new Impl(content);
                nameToSource.put(path, new WeakReference<>(source));
                return source;
            }
        }
        if (source.content() instanceof ClientManagedFileSourceImpl) {
            final ClientManagedFileSourceImpl modifiableSource = (ClientManagedFileSourceImpl) source.content();
            modifiableSource.setCode(chars);
            source.clearTextMap();
            return source;
        } else {
            throw new IOException("Attempt to modify contents of a file Source");
        }
    }

    /**
     * Creates an anonymous source from literal text: not named and not indexed.
     *
     * @param chars textual source code
     * @param description a note about the origin, for error messages and debugging
     * @return a newly created, non-indexed source representation
     * @since 0.8 or earlier
     */
    public static Source fromText(CharSequence chars, String description) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromText from compiled code");
        Content content = new LiteralSourceImpl(description, chars.toString());
        return new Impl(content);
    }

    /**
     * Creates an anonymous source from literal text that is provided incrementally after creation:
     * not named and not indexed.
     *
     * @param description a note about the origin, for error messages and debugging
     * @return a newly created, non-indexed, initially empty, appendable source representation
     * @since 0.8 or earlier
     */
    public static Source fromAppendableText(String description) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromAppendableText from compiled code");
        return new AppendableLiteralSourceImpl(description);
    }

    /**
     * Creates a source from literal text that can be retrieved by name, with no assumptions about
     * the structure or meaning of the name. If the name is already in the index, the new instance
     * will replace the previously existing instance in the index.
     *
     * @param chars textual source code
     * @param name string to use for indexing/lookup
     * @return a newly created, source representation
     * @since 0.8 or earlier
     */
    public static Source fromNamedText(CharSequence chars, String name) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromNamedText from compiled code");
        Content content = new LiteralSourceImpl(name, chars.toString());
        final Source source = new Impl(content);
        nameToSource.put(name, new WeakReference<>(source));
        return source;
    }

    /**
     * Creates a source from literal text that is provided incrementally after creation and which
     * can be retrieved by name, with no assumptions about the structure or meaning of the name. If
     * the name is already in the index, the new instance will replace the previously existing
     * instance in the index.
     *
     * @param name string to use for indexing/lookup
     * @return a newly created, indexed, initially empty, appendable source representation
     * @since 0.8 or earlier
     */
    public static Source fromNamedAppendableText(String name) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromNamedAppendable from compiled code");
        final Source source = new AppendableLiteralSourceImpl(name);
        nameToSource.put(name, new WeakReference<>(source));
        return source;
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range of
     * an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @param length the number of characters in the sub-range
     * @return a new instance representing a sub-range of another Source
     * @throws IllegalArgumentException if the specified sub-range is not contained in the base
     * @since 0.8 or earlier
     */
    public static Source subSource(Source base, int baseCharIndex, int length) {
        CompilerAsserts.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);
        final SubSourceImpl subSource = SubSourceImpl.create(base, baseCharIndex, length);
        return subSource;
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range at
     * the end of an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @return a new instance representing a sub-range at the end of another Source
     * @throws IllegalArgumentException if the index is out of range
     * @since 0.8 or earlier
     */
    public static Source subSource(Source base, int baseCharIndex) {
        CompilerAsserts.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);

        return subSource(base, baseCharIndex, base.getLength() - baseCharIndex);
    }

    /**
     * Creates a source whose contents will be read immediately from a URL and cached.
     *
     * @param url
     * @param description identifies the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     * @since 0.8 or earlier
     */
    public static Source fromURL(URL url, String description) throws IOException {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromURL from compiled code");
        return URLSourceImpl.get(url, description);
    }

    /**
     * Creates a source whose contents will be read immediately and cached.
     *
     * @param reader
     * @param description a note about the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     * @since 0.8 or earlier
     */
    public static Source fromReader(Reader reader, String description) throws IOException {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromReader from compiled code");
        Content content = new LiteralSourceImpl(description, read(reader));
        return new Impl(content);
    }

    /**
     * Creates a source from raw bytes. This can be used if the encoding of strings in your language
     * is not compatible with Java strings, or if your parser returns byte indices instead of
     * character indices. The returned source is then indexed by byte, not by character.
     *
     * @param bytes the raw bytes of the source
     * @param description a note about the origin, possibly useful for debugging
     * @param charset how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     * @since 0.8 or earlier
     */
    public static Source fromBytes(byte[] bytes, String description, Charset charset) {
        return fromBytes(bytes, 0, bytes.length, description, charset);
    }

    /**
     * Creates a source from raw bytes. This can be used if the encoding of strings in your language
     * is not compatible with Java strings, or if your parser returns byte indices instead of
     * character indices. The returned source is then indexed by byte, not by character. Offsets are
     * relative to byteIndex.
     *
     * @param bytes the raw bytes of the source
     * @param byteIndex where the string starts in the byte array
     * @param length the length of the string in the byte array
     * @param description a note about the origin, possibly useful for debugging
     * @param charset how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     * @since 0.8 or earlier
     */
    public static Source fromBytes(byte[] bytes, int byteIndex, int length, String description, Charset charset) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromBytes from compiled code");
        return new BytesSourceImpl(description, bytes, byteIndex, length, charset);
    }

    // TODO (mlvdv) enable per-file choice whether to cache?
    /**
     * Enables/disables caching of file contents, <em>disabled</em> by default. Caching of sources
     * created from literal text or readers is always enabled.
     *
     * @since 0.8 or earlier
     */
    public static void setFileCaching(boolean enabled) {
        fileCacheEnabled = enabled;
    }

    static String read(Reader reader) throws IOException {
        final BufferedReader bufferedReader = new BufferedReader(reader);
        final StringBuilder builder = new StringBuilder();
        final char[] buffer = new char[1024];

        try {
            while (true) {
                final int n = bufferedReader.read(buffer);
                if (n == -1) {
                    break;
                }
                builder.append(buffer, 0, n);
            }
        } finally {
            bufferedReader.close();
        }
        return builder.toString();
    }

    Source() {
    }

    Source(String mimeType) {
        this.mimeType = mimeType;
    }

    Content content() {
        return null;
    }

    void reset() {
        content().reset();
    }

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file.
     *
     * @return the name of the guest language program
     * @since 0.8 or earlier
     */
    public String getName() {
        return content().getName();
    }

    /**
     * Returns a short version of the name of the resource holding a guest language program (as
     * described in {@link #getName()}). For example, this could be just the name of the file,
     * rather than a full path.
     *
     * @return the short name of the guest language program
     * @since 0.8 or earlier
     */
    public String getShortName() {
        return content().getShortName();
    }

    /**
     * The normalized, canonical name if the source is a file.
     *
     * @since 0.8 or earlier
     */
    public String getPath() {
        return content().getPath();
    }

    /**
     * The URL if the source is retrieved via URL.
     *
     * @return URL or <code>null</code>
     * @since 0.8 or earlier
     */
    public URL getURL() {
        return content().getURL();
    }

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public Reader getReader() {
        try {
            return content().getReader();
        } catch (final IOException ex) {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    throw ex;
                }

                @Override
                public void close() throws IOException {
                }
            };
        }
    }

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public final InputStream getInputStream() {
        return new ByteArrayInputStream(getCode().getBytes());
    }

    /**
     * Gets the number of characters in the source.
     *
     * @since 0.8 or earlier
     */
    public final int getLength() {
        return getTextMap().length();
    }

    /**
     * Returns the complete text of the code.
     *
     * @since 0.8 or earlier
     */
    public String getCode() {
        return content().getCode();
    }

    /**
     * Returns a subsection of the code test.
     *
     * @since 0.8 or earlier
     */
    public String getCode(int charIndex, int charLength) {
        return getCode().substring(charIndex, charIndex + charLength);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     *
     * @since 0.8 or earlier
     */
    public final String getCode(int lineNumber) {
        final int offset = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return getCode().substring(offset, offset + length);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line.
     *
     * @since 0.8 or earlier
     */
    public final int getLineCount() {
        return getTextMap().lineCount();
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public final int getLineNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToLine(offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public final int getColumnNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToCol(offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public final int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineStartOffset(lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public final int getLineLength(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineLength(lineNumber);
    }

    /**
     * Append text to a Source explicitly created as <em>Appendable</em>.
     *
     * @param chars the text to append
     * @throws UnsupportedOperationException by concrete subclasses that do not support appending
     * @since 0.8 or earlier
     */
    public void appendCode(CharSequence chars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a representation of a contiguous region of text in the source.
     * <p>
     * This method performs no checks on the validity of the arguments.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param charIndex the 0-based index of the first character of the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @since 0.8 or earlier
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length) {
        checkRange(charIndex, length);
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * @deprecated tags are now determined by {@link Node#isTaggedWith(Class)}. Use
     *             {@link #createSection(String, int, int, int, int)} instead.
     * @since 0.12
     */
    @Deprecated
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length, String... tags) {
        checkRange(charIndex, length);
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, tags);
    }

    private SourceSection createSectionImpl(String identifier, int startLine, int startColumn, int charIndex, int length, String[] tags) {
        return new SourceSection(null, this, identifier, startLine, startColumn, charIndex, length, tags);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code charIndex} value by building a {@code TextMap map} of lines in the source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if arguments are outside the text of the source
     * @throws IllegalStateException if the source is one of the "null" instances
     * @since 0.8 or earlier
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int length) {
        final int lineStartOffset = getTextMap().lineStartOffset(startLine);
        if (startColumn > getTextMap().lineLength(startLine)) {
            throw new IllegalArgumentException("column out of range");
        }
        final int startOffset = lineStartOffset + startColumn - 1;
        return createSectionImpl(identifier, startLine, startColumn, startOffset, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code (startLine, startColumn)} values by building a {@code TextMap map} of lines in the
     * source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     *
     * @param identifier terse description of the region
     * @param charIndex 0-based position of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if either of the arguments are outside the text of the
     *             source
     * @throws IllegalStateException if the source is one of the "null" instances
     * @since 0.8 or earlier
     */
    public final SourceSection createSection(String identifier, int charIndex, int length) throws IllegalArgumentException {
        return createSection(identifier, charIndex, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * @deprecated tags are now determined by {@link Node#isTaggedWith(Class)}. Use
     *             {@link #createSection(String, int, int)} instead.
     * @since 0.12
     */
    @Deprecated
    public final SourceSection createSection(String identifier, int charIndex, int length, String... tags) throws IllegalArgumentException {
        checkRange(charIndex, length);
        final int startLine = getLineNumber(charIndex);
        final int startColumn = charIndex - getLineStartOffset(startLine) + 1;
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, tags);
    }

    void checkRange(int charIndex, int length) {
        if (!(charIndex >= 0 && length >= 0 && charIndex + length <= getCode().length())) {
            throw new IllegalArgumentException("text positions out of range");
        }
    }

    /**
     * Creates a representation of a line of text in the source identified only by line number, from
     * which the character information will be computed.
     *
     * @param identifier terse description of the line
     * @param lineNumber 1-based line number of the first character in the section
     * @return newly created object representing the specified line
     * @throws IllegalArgumentException if the line does not exist the source
     * @throws IllegalStateException if the source is one of the "null" instances
     * @since 0.8 or earlier
     */
    public final SourceSection createSection(String identifier, int lineNumber) {
        final int charIndex = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return createSection(identifier, charIndex, length);
    }

    /**
     * Creates a representation of a line number in this source, suitable for use as a hash table
     * key with equality defined to mean equivalent location.
     *
     * @param lineNumber a 1-based line number in this source
     * @return a representation of a line in this source
     * @since 0.8 or earlier
     */
    public final LineLocation createLineLocation(int lineNumber) {
        return new LineLocation(this, lineNumber);
    }

    /**
     * An object suitable for using as a key into a hashtable that defines equivalence between
     * different source types.
     */
    Object getHashKey() {
        return content() == null ? getName() : content().getHashKey();
    }

    final TextMap getTextMap() {
        if (textMap == null) {
            textMap = createTextMap();
        }
        return textMap;
    }

    final void clearTextMap() {
        textMap = null;
    }

    TextMap createTextMap() {
        final String code = getCode();
        if (code == null) {
            throw new RuntimeException("can't read file " + getName());
        }
        return TextMap.fromString(code);
    }

    /**
     * Associates the source with specified MIME type. The mime type may be used to select the right
     * {@link Registration Truffle language} to use to execute the returned source. The value of
     * MIME type can be obtained via {@link #getMimeType()} method.
     *
     * @param mime mime type to use
     * @return new (identical) source, just associated {@link #getMimeType()}
     * @since 0.8 or earlier
     */
    public final Source withMimeType(String mime) {
        try {
            Source another = (Source) clone();
            another.mimeType = mime;
            return another;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * MIME type that is associated with this source. By default file extensions known to the system
     * are used to determine the MIME type (via registered {@link FileTypeDetector} classes), yet
     * one can directly {@link #withMimeType(java.lang.String) provide a MIME type} to each source.
     *
     * @return MIME type of this source or <code>null</code>, if unknown
     * @since 0.8 or earlier
     */
    public String getMimeType() {
        if (mimeType == null) {
            mimeType = findMimeType();
        }
        return mimeType;
    }

    String findMimeType() {
        return null;
    }

    final boolean equalMime(Source other) {
        if (mimeType == null) {
            return other.mimeType == null;
        }
        return mimeType.equals(other.mimeType);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Source) {
            Source other = (Source) obj;
            if (content() == null) {
                return super.equals(obj);
            }
            return content().equals(other.content()) && equalMime(other);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (content() == null) {
            return super.hashCode();
        }
        return content().hashCode();
    }

    private static class Impl extends Source implements Cloneable {
        private final Content content;

        Impl(Content content) {
            this.content = content;
        }

        @Override
        Content content() {
            return content;
        }
    }
}

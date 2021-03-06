/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.header;

import java.nio.charset.Charset;
import java.util.List;

import org.forgerock.openig.http.Message;

/**
 * Processes the <strong>{@code Content-Type}</strong> message header. For more information,
 * see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a> §14.17.
 */
public class ContentTypeHeader implements Header {

    /** The name of the header that this object represents. */
    public static final String NAME = "Content-Type";

    /** The type/sub-type of the message. */
    private String type = null;

    /** The character set used in encoding the message. */
    private String charset = null;

    /** The boundary value provided in multipart messages. */
    private String boundary = null;

    /**
     * Constructs a new empty header.
     */
    public ContentTypeHeader() {
    }

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message the message to initialize the header from.
     */
    public ContentTypeHeader(Message<?> message) {
        fromMessage(message);
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string the value to initialize the header from.
     */
    public ContentTypeHeader(String string) {
        fromString(string);
    }

    /**
     * Returns the media type of the underlying data or {@code null} if none specified.
     *
     * @return The media type of the underlying data or {@code null} if none specified.
     */
    public String getType() {
        return type != null ? type : null;
    }

    /**
     * Returns the character set encoding used to encode the message, or {@code null} if no character set was specified.
     *
     * @throws java.nio.charset.IllegalCharsetNameException
     *             if the given charset name is illegal.
     * @throws java.nio.charset.UnsupportedCharsetException
     *             if no support for the named charset is available.
     * @return The character set encoding used to encode the message or {@code null} if empty.
     */
    public Charset getCharset() {
        return charset != null ? Charset.forName(charset) : null;
    }

    /**
     * Returns the encapsulation boundary or {@code null} if none specified.
     *
     * @return The encapsulation boundary or {@code null} if none specified.
     */
    public String getBoundary() {
        return boundary != null ? boundary : null;
    }

    private void clear() {
        type = null;
        charset = null;
        boundary = null;
    }

    @Override
    public String getKey() {
        return NAME;
    }

    @Override
    public void fromMessage(Message<?> message) {
        if (message != null && message.getHeaders() != null) {
            fromString(message.getHeaders().getFirst(NAME));
        }
    }

    @Override
    public void fromString(String string) {
        clear();
        List<String> parts = HeaderUtil.split(string, ';');
        if (parts.size() > 0) {
            type = parts.get(0);
            charset = HeaderUtil.parseParameters(parts).get("charset");
            boundary = HeaderUtil.parseParameters(parts).get("boundary");
        }
    }

    @Override
    public void toMessage(Message<?> message) {
        String value = toString();
        if (value != null) {
            message.getHeaders().putSingle(NAME, value);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (type != null) {
            sb.append(type);
            if (charset != null) {
                sb.append("; charset=").append(charset);
            }
            if (boundary != null) {
                sb.append("; boundary=").append(boundary);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof ContentTypeHeader)) {
            return false;
        }
        ContentTypeHeader ct = (ContentTypeHeader) o;
        return ((type == null && ct.type == null)
                || (type != null && type.equals(ct.type)))
                && ((charset == null && ct.charset == null)
                || (charset != null && charset.equals(ct.charset)))
                && ((boundary == null && ct.boundary == null)
                || (boundary != null && boundary.equals(ct.boundary)));
    }

    @Override
    public int hashCode() {
        return (type == null ? 0 : type.hashCode())
                ^ (charset == null ? 0 : charset.hashCode())
                ^ (boundary == null ? 0 : boundary.hashCode());
    }
}

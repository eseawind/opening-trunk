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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.*;
import static org.forgerock.openig.util.URIUtil.*;
import static org.forgerock.util.Utils.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.RedirectFilter;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.header.LocationHeader;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;

/**
 * Utility methods used by classes in this package.
 */
final class OAuth2Utils {

    static URI buildUri(final Exchange exchange, final Expression uriExpression)
            throws HandlerException {
        return buildUri(exchange, uriExpression, null);
    }

    static URI buildUri(final Exchange exchange, final Expression uriExpression,
            final String additionalPath) throws HandlerException {
        try {
            String uriString = uriExpression.eval(exchange, String.class);
            if (uriString == null) {
                throw new HandlerException("Unable to evaluate URI expression");
            }
            if (additionalPath != null) {
                if (uriString.endsWith("/")) {
                    uriString += additionalPath;
                } else {
                    uriString += "/" + additionalPath;
                }
            }
            // Make sure we don't change the request's URI but return a new URI
            return exchange.request.getUri().asURI().resolve(new URI(uriString));
        } catch (final URISyntaxException e) {
            throw new HandlerException(e);
        }
    }

    static JsonValue getJsonContent(final Response response) throws OAuth2ErrorException {
        try {
            return new JsonValue(response.getEntity().getJson()).expect(Map.class);
        } catch (final Exception e) {
            throw new OAuth2ErrorException(E_SERVER_ERROR,
                    "Received a malformed JSON response from the authorization server", e);
        } finally {
            closeSilently(response);
        }
    }

    static List<String> getScopes(final Exchange exchange, final List<Expression> scopeExpressions)
            throws HandlerException {
        final List<String> scopeValues = new ArrayList<String>(scopeExpressions.size());
        for (final Expression scope : scopeExpressions) {
            final String result = scope.eval(exchange, String.class);
            if (result == null) {
                throw new HandlerException("Unable to determine the scope");
            }
            scopeValues.add(result);
        }
        return scopeValues;
    }

    static void httpRedirect(final Exchange exchange, final String uri) {
        // FIXME: this constant should in HTTP package?
        httpResponse(exchange, RedirectFilter.REDIRECT_STATUS_302);
        exchange.response.getHeaders().add(LocationHeader.NAME, uri);
    }

    static void httpResponse(final Exchange exchange, final int status) {
        closeSilently(exchange.response);
        exchange.response = new Response();
        exchange.response.setStatus(status);
    }

    static boolean matchesUri(final Exchange exchange, final URI uri) {
        final URI pathOnly = withoutQueryAndFragment(uri);
        final URI requestPathOnly = withoutQueryAndFragment(exchange.request.getUri().asURI());
        return pathOnly.equals(requestPathOnly);
    }

    private OAuth2Utils() {
        // Prevent instantiation.
    }

}

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
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.MutableUri;
import org.forgerock.openig.util.StringUtil;

/**
 * Suppresses, relays and manages cookies. The names of filtered cookies are stored in one of
 * three action set variables: {@code suppressed}, {@code relayed} and {@code managed}. If a
 * cookie is not found in any of the action sets, then a default action is selected.
 * <p>
 * The default action is controlled by setting the {@code defaultAction} field. The default
 * action at initialization is to manage all cookies. In the event a cookie appears in more
 * than one action set, then it will be selected in order of precedence: managed, suppressed,
 * relayed.
 * <p>
 * Managed cookies are intercepted by the cookie filter itself and stored in the request
 * {@link Session} object. The default {@code policy} is to accept all incoming cookies, but
 * can be changed to others as appropriate.
 */
public class CookieFilter extends GenericFilter {

    /** Action to be performed for a cookie. */
    public enum Action {
        /** Intercept and manage the cookie within the proxy. */
        MANAGE,
        /** Remove the cookie from request and response. */
        SUPPRESS,
        /** Relay the cookie between remote client and remote host. */
        RELAY
    }

// TODO: Use the org.forgerock.openig.header framework now for parsing, not regexes anymore.

    /** Splits string using comma delimiter, outside of quotes. */
    private static final Pattern DELIM_COMMA = Pattern.compile(",(?=([^\"]*\"[^\"]*\")*(?![^\"]*\"))");

    /** Splits string using equals sign delimiter, outside of quotes. */
    private static final Pattern DELIM_EQUALS = Pattern.compile("=(?=([^\"]*\"[^\"]*\")*(?![^\"]*\"))");

    /** Splits string using semicolon delimiter, outside of quotes. */
    private static final Pattern DELIM_SEMICOLON = Pattern.compile(";(?=([^\"]*\"[^\"]*\")*(?![^\"]*\"))");

    /** Splits string using colon delimiter. */
    private static final Pattern DELIM_COLON = Pattern.compile(":");

    /** Response headers to parse. */
    private static final String[] RESPONSE_HEADERS = {"Set-Cookie", "Set-Cookie2"};

    /** Action to perform for cookies that do not match an action set. Default: manage. */
    private Action defaultAction = Action.MANAGE;

    /** The policy for managed cookies. Default: accept all cookies. */
    private CookiePolicy policy = CookiePolicy.ACCEPT_ALL;

    /** Action set for cookies to be suppressed. */
    private final CaseInsensitiveSet suppressed = new CaseInsensitiveSet();

    /** Action set for cookies to be relayed. */
    private final CaseInsensitiveSet relayed = new CaseInsensitiveSet();

    /** Action set for cookies that filter should intercept and manage. */
    private final CaseInsensitiveSet managed = new CaseInsensitiveSet();

    /**
     * Set the action to perform for cookies that do not match an action set. Default: {@link Action#MANAGE}.
     * @param defaultAction the action to perform for cookies that do not match an action set.
     */
    public void setDefaultAction(final Action defaultAction) {
        this.defaultAction = defaultAction;
    }

    /**
     * Set the policy for managed cookies. Default: accept all cookies ({@link CookiePolicy#ACCEPT_ALL}).
     * @param policy the policy for managed cookies.
     */
    public void setPolicy(final CookiePolicy policy) {
        this.policy = policy;
    }

    /**
     * Returns the set of cookie names that will be suppressed from the request and from the response.
     * @return the set of suppressed cookie identifiers.
     */
    public CaseInsensitiveSet getSuppressed() {
        return suppressed;
    }

    /**
     * Returns the set of cookie names that will be relayed ({@literal Cookie} transmitted from the
     * client to the next handler in the context of a request, and {@literal Set-Cookie2} transmitted
     * from the next handler to the client in the context of a response).
     * @return the set of relayed cookie identifiers.
     */
    public CaseInsensitiveSet getRelayed() {
        return relayed;
    }

    /**
     * Returns the set of cookie names that will be managed.
     * @return the set of managed cookie identifiers.
     */
    public CaseInsensitiveSet getManaged() {
        return managed;
    }

    /**
     * Resolves the request URI based on the request URI variable and optional
     * Host header. This allows the request URI to contain a raw IP address,
     * while the Host header resolves the hostname and port that the remote
     * client used to access it.
     * <p>
     * Note: This method returns a normalized URI, as though returned by the
     * {@link URI#normalize} method.
     *
     * @return the resolved URI value.
     */
// TODO: Rewrite and put in URIutil.
    private MutableUri resolveHostURI(Request request) {
        MutableUri uri = request.getUri();
        String header = (request.getHeaders() != null ? request.getHeaders().getFirst("Host") : null);
        if (uri != null && header != null) {
            String[] hostport = DELIM_COLON.split(header, 2);
            int port;
            try {
                port = (hostport.length == 2 ? Integer.parseInt(hostport[1]) : -1);
            } catch (NumberFormatException nfe) {
                port = -1;
            }
            try {
                uri = new MutableUri(uri.getScheme(),
                                     null,
                                     hostport[0],
                                     port,
                                     "/",
                                     null,
                                     null).resolve(new MutableUri(uri.getScheme(),
                                                                  null,
                                                                  uri.getHost(),
                                                                  uri.getPort(),
                                                                  null,
                                                                  null,
                                                                  null).relativize(uri));
            } catch (URISyntaxException use) {
                // suppress exception
            }
        }
        return uri;
    }

    /**
     * Sets all request cookies (existing in request plus those to add from cookie jar) in
     * a single "Cookie" header in the request.
     */
    private void addRequestCookies(CookieManager manager, MutableUri resolved, Request request) throws IOException {
        List<String> cookies = request.getHeaders().get("Cookie");
        if (cookies == null) {
            cookies = new ArrayList<String>();
        }
        List<String> managed = manager.get(resolved.asURI(), request.getHeaders()).get("Cookie");
        if (managed != null) {
            cookies.addAll(managed);
        }
        StringBuilder sb = new StringBuilder();
        for (String cookie : cookies) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(cookie);
        }
        if (sb.length() > 0) {
            // replace any existing header(s)
            request.getHeaders().putSingle("Cookie", sb.toString());
        }
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        // resolve to client-supplied host header
        MutableUri resolved = resolveHostURI(exchange.request);
        // session cookie jar
        CookieManager manager = getManager(exchange.session);
        // remove cookies that are suppressed or managed
        suppress(exchange.request);
        // add any request cookies to header
        addRequestCookies(manager, resolved, exchange.request);
        // pass exchange to next handler in chain
        next.handle(exchange);
        // manage cookie headers in response
        manager.put(resolved.asURI(), exchange.response.getHeaders());
        // remove cookies that are suppressed or managed
        suppress(exchange.response);
        timer.stop();
    }

    /**
     * Computes what action to perform for the specified cookie name.
     *
     * @param name the name of the cookie to compute action for.
     * @return the computed action to perform for the given cookie.
     */
    private Action action(String name) {
        if (managed.contains(name)) {
            return Action.MANAGE;
        } else if (suppressed.contains(name)) {
            return Action.SUPPRESS;
        } else if (relayed.contains(name)) {
            return Action.RELAY;
        } else {
            return defaultAction;
        }
    }

    /**
     * Returns the cookie manager for the session, creating one if it does not already exist.
     *
     * @param session the session that contains the cookie manager.
     * @return the retrieved (or created) cookie manager.
     */
    private CookieManager getManager(Session session) {
        CookieManager manager = null;
        // prevent a race for the cookie manager
        synchronized (session) {
            manager = (CookieManager) session.get(CookieManager.class.getName());
            if (manager == null) {
                manager = new CookieManager(null, new CookiePolicy() {
                    public boolean shouldAccept(URI uri, HttpCookie cookie) {
                        return (action(cookie.getName()) == Action.MANAGE && policy.shouldAccept(uri, cookie));
                    }
                });
                session.put(CookieManager.class.getName(), manager);
            }
        }
        return manager;
    }

    /**
     * Removes the cookies from the request that are suppressed or managed.
     *
     * @param request the request to suppress the cookies in.
     */
    private void suppress(Request request) {
        List<String> headers = request.getHeaders().get("Cookie");
        if (headers != null) {
            for (ListIterator<String> hi = headers.listIterator(); hi.hasNext();) {
                String header = hi.next();
                ArrayList<String> parts = new ArrayList<String>(Arrays.asList(DELIM_SEMICOLON.split(header, 0)));
                int originalSize = parts.size();
                boolean remove = false;
                int intact = 0;
                for (ListIterator<String> pi = parts.listIterator(); pi.hasNext();) {
                    String part = pi.next().trim();
                    if (part.length() != 0 && part.charAt(0) == '$') {
                        if (remove) {
                            pi.remove();
                        }
                    } else {
                        Action action = action((DELIM_EQUALS.split(part, 2))[0].trim());
                        if (action == Action.SUPPRESS || action == Action.MANAGE) {
                            pi.remove();
                            remove = true;
                        } else {
                            intact++;
                            remove = false;
                        }
                    }
                }
                if (intact == 0) {
                    hi.remove();
                } else if (parts.size() != originalSize) {
                    hi.set(StringUtil.join(";", parts));
                }
            }
            if (headers.isEmpty()) {
                request.getHeaders().remove("Cookie");
            }
        }
    }

    /**
     * Removes the cookies from the response that are suppressed or managed.
     *
     * @param response the response to suppress the cookies in.
     */
    private void suppress(Response response) {
        for (String name : RESPONSE_HEADERS) {
            List<String> headers = response.getHeaders().get(name);
            if (headers != null) {
                for (ListIterator<String> hi = headers.listIterator(); hi.hasNext();) {
                    String header = hi.next();
                    ArrayList<String> parts;
                    if (name.equals("Set-Cookie2")) {
                        // RFC 2965 cookie
                        parts = new ArrayList<String>(Arrays.asList(DELIM_COMMA.split(header, 0)));
                    } else {
                        // Netscape cookie
                        parts = new ArrayList<String>();
                        parts.add(header);
                    }
                    int originalSize = parts.size();
                    for (ListIterator<String> pi = parts.listIterator(); pi.hasNext();) {
                        String part = pi.next();
                        Action action = action((DELIM_EQUALS.split(part, 2))[0].trim());
                        if (action == Action.SUPPRESS || action == Action.MANAGE) {
                            pi.remove();
                        }
                    }
                    if (parts.size() == 0) {
                        hi.remove();
                    } else if (parts.size() != originalSize) {
                        hi.set(StringUtil.join(",", parts));
                    }
                }
            }
        }
    }

    /**
     * Creates and initializes a cookie filter in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            CookieFilter filter = new CookieFilter();
            filter.suppressed.addAll(config.get("suppressed")
                    .defaultTo(Collections.emptyList())
                    .asList(String.class));
            filter.relayed.addAll(config.get("relayed")
                    .defaultTo(Collections.emptyList())
                    .asList(String.class));
            filter.managed.addAll(config.get("managed")
                    .defaultTo(Collections.emptyList())
                    .asList(String.class));
            filter.defaultAction = config.get("defaultAction")
                    .defaultTo(filter.defaultAction.toString())
                    .asEnum(Action.class);
            return filter;
        }
    }
}

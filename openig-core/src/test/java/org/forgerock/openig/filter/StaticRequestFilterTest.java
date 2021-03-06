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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.util.MutableUri.uri;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class StaticRequestFilterTest {

    public static final String URI = "http://openig.forgerock.org";
    @Mock
    private Handler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Minimal configuration test.
     * The handler should propagate the uri, method and version.
     */
    @Test
    public void testUriMethodAndVersionPropagation() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(new Expression(URI));
        filter.setVersion("1.1");

        Exchange exchange = new Exchange();
        filter.filter(exchange, terminalHandler);

        assertThat(exchange.request).isNotNull();
        assertThat(exchange.request.getUri()).isEqualTo(uri(URI));
        assertThat(exchange.request.getMethod()).isEqualTo("GET");
        assertThat(exchange.request.getVersion()).isEqualTo("1.1");
    }

    @Test
    public void testHeadersPropagation() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(new Expression(URI));
        filter.addHeaderValue("Mono-Valued", new Expression("First Value"));
        filter.addHeaderValue("Multi-Valued", new Expression("One (1)"));
        filter.addHeaderValue("Multi-Valued", new Expression("Two (${exchange.request.version})"));

        Exchange exchange = new Exchange();
        // Needed to verify expression evaluation
        Request original = new Request();
        original.setVersion("2");
        exchange.request = original;

        filter.filter(exchange, terminalHandler);

        // Verify that the request have been replaced
        // And check that headers have been properly populated
        assertThat(exchange.request).isNotSameAs(original);
        assertThat(exchange.request.getHeaders().get("Mono-Valued"))
                .hasSize(1)
                .containsOnly("First Value");
        assertThat(exchange.request.getHeaders().get("Multi-Valued"))
                .hasSize(2)
                .containsOnly("One (1)", "Two (2)");
    }

    @Test
    public void testFormAttributesPropagationWithGetMethod() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(new Expression(URI));
        filter.addFormParameter("mono", new Expression("one"));
        filter.addFormParameter("multi", new Expression("one1"));
        filter.addFormParameter("multi", new Expression("two${exchange.request.version}"));

        Exchange exchange = new Exchange();
        // Needed to verify expression evaluation
        exchange.request = new Request();
        exchange.request.setVersion("2");

        filter.filter(exchange, terminalHandler);

        // Verify that the new request URI contains the form's fields
        assertThat(exchange.request.getUri().toString())
                .startsWith(URI)
                .contains("mono=one")
                .contains("multi=one1")
                .contains("multi=two2");
        assertThat(exchange.request.getEntity().getString()).isEmpty();
    }

    @Test
    public void testFormAttributesPropagationWithPostMethod() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(new Expression(URI));
        filter.addFormParameter("mono", new Expression("one"));
        filter.addFormParameter("multi", new Expression("one1"));
        filter.addFormParameter("multi", new Expression("two${exchange.request.version}"));

        Exchange exchange = new Exchange();
        // Needed to verify expression evaluation
        Request original = new Request();
        original.setVersion("2");
        exchange.request = original;

        filter.filter(exchange, terminalHandler);

        // Verify that the new request entity contains the form's fields
        assertThat(exchange.request.getMethod()).isEqualTo("POST");
        assertThat(exchange.request.getHeaders().getFirst("Content-Type")).isEqualTo(
                "application/x-www-form-urlencoded");
        assertThat(exchange.request.getEntity().getString())
                .contains("mono=one")
                .contains("multi=one1")
                .contains("multi=two2");
    }

    @Test
    public void testRequestSaveAndRestore() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(new Expression(URI));
        filter.setRestore(true);

        Exchange exchange = new Exchange();
        Request original = new Request();
        exchange.request = original;

        filter.filter(exchange, terminalHandler);

        // Verify that the original request is restored
        assertThat(exchange.request).isSameAs(original);
    }

    @Test
    public void testRequestDisabledSaveAndRestore() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(new Expression(URI));
        filter.setRestore(false);

        Exchange exchange = new Exchange();
        Request original = new Request();
        exchange.request = original;

        filter.filter(exchange, terminalHandler);

        // Verify that the original request is not restored
        assertThat(exchange.request).isNotSameAs(original);
    }
}

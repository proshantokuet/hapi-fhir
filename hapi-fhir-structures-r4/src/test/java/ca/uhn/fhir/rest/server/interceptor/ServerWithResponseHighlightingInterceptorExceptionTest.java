package ca.uhn.fhir.rest.server.interceptor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.PortUtil;
import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.util.UrlUtil;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

public class ServerWithResponseHighlightingInterceptorExceptionTest {
	private static CloseableHttpClient ourClient;

	private static FhirContext ourCtx = FhirContext.forR4();
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ServerWithResponseHighlightingInterceptorExceptionTest.class);
	private static int ourPort;
	private static Server ourServer;
	private static RestfulServer ourServlet;

	@Test
	public void testExpectedException() throws Exception {
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/123");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);
		
		assertEquals(400, status.getStatusLine().getStatusCode());
		assertThat(responseContent, containsString("<diagnostics value=\"AAABBB\"/>"));
	}


	@Test
	public void testUnexpectedException() throws Exception {
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?identifier=123");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);
		
		assertEquals(500, status.getStatusLine().getStatusCode());
		assertThat(responseContent, containsString("<diagnostics value=\"Failed to call access method: java.lang.Error: AAABBB\"/>"));
	}

	@Test
	public void testPreventHtmlInjectionViaInvalidResourceType() throws Exception {
	// XML
		HttpGet httpGet = new HttpGet(
			"http://localhost:" +
				ourPort +
				"/AA" +
				UrlUtil.escapeUrlParam("<script>"));
		httpGet.addHeader(Constants.HEADER_ACCEPT, Constants.CT_HTML+", " +Constants.CT_FHIR_XML_NEW);
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), Charsets.UTF_8);
			ourLog.info(responseContent);

			assertEquals(404, status.getStatusLine().getStatusCode());
			assertThat(responseContent, not(containsString("<script>>")));
		}

		// JSON
		httpGet = new HttpGet(
			"http://localhost:" +
				ourPort +
				"/AA" +
				UrlUtil.escapeUrlParam("<script>"));
		httpGet.addHeader(Constants.HEADER_ACCEPT, Constants.CT_HTML+", " +Constants.CT_FHIR_JSON_NEW);
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), Charsets.UTF_8);
			ourLog.info(responseContent);

			assertEquals(404, status.getStatusLine().getStatusCode());
			assertThat(responseContent, not(containsString("<script>>")));
		}
	}

	@AfterClass
	public static void afterClassClearContext() throws Exception {
		ourServer.stop();
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {

		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);

		DummyPatientResourceProvider patientProvider = new DummyPatientResourceProvider();

		ServletHandler proxyHandler = new ServletHandler();
		ourServlet = new RestfulServer(ourCtx);
		ourServlet.setFhirContext(ourCtx);
		ourServlet.setResourceProviders(patientProvider);
		ourServlet.registerInterceptor(new ResponseHighlighterInterceptor());
		ServletHolder servletHolder = new ServletHolder(ourServlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}

	public static class DummyPatientResourceProvider implements IResourceProvider {

		@Override
		public Class<? extends Patient> getResourceType() {
			return Patient.class;
		}

		@Read
		public Patient read(@IdParam IdType theId) {
			throw new InvalidRequestException("AAABBB");
		}

		@Search
		public Patient search(@RequiredParam(name="identifier") TokenParam theToken) {
			throw new Error("AAABBB");
		}


	}

}
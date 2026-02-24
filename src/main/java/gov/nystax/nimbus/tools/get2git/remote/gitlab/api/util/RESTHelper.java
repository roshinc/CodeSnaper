/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.response.RESTResponse;

/**
 * @author t63606
 *
 */
public class RESTHelper {

	/**
	 * Does a get request and returns the string output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	public static <T> RESTResponse<T> doGet(final String urlString, final Map<String, String> parameters,
			final Map<String, String> headers) throws G2GClientException, G2GRestException {
		return doGetInternal(urlString, parameters, headers, false);
	}

	/**
	 * Does a get request and returns the string output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	public static <T> RESTResponse<T> doPut(final Map<String, String> body, final String urlString,
			final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		return doPutInternal(body, urlString, parameters, headers, false);
	}

	/**
	 * Does a post request and returns the string output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	public static <T> RESTResponse<T> doPost(final Map<String, String> body, final String urlString,
			final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		return doPostInternal(body, urlString, parameters, headers, false);
	}

	/**
	 * Does a post request and returns the string output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	public static <T> RESTResponse<T> doPost(final String body, final String urlString,
			final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		return doPostInternal(body, urlString, parameters, headers, false);
	}

	/**
	 * Does a patch request and returns the string output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	public static <T> RESTResponse<T> doPatch(final Map<String, List<Map<String, String>>> body, final String urlString,
			final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		return doPatchInternal(body, urlString, parameters, headers, false);
	}

	/**
	 * Does a get request and returns the byte[] output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	public static <T> RESTResponse<T> doGetForBytes(final String urlString, final Map<String, String> parameters,
			final Map<String, String> headers) throws URISyntaxException, InterruptedException, IOException {
		return doGetInternal(urlString, parameters, headers, true);
	}

	/**
	 * Create a {@linkplain HttpRequest.Builder} instance configured by the values
	 * passed in.
	 * 
	 * @param method     the request method
	 * @param urlString  the request target
	 * @param parameters parameters (if any) for the request
	 * @param headers    headers (if any) for the request
	 * @param publisher  the body publisher for this request, ignored if not
	 *                   applicable
	 * @return a configured {@linkplain HttpRequest.Builder
	 * @throws G2GClientException If {@code urlString} is an invalid
	 *                            {@linkplain URI}
	 */
	private static HttpRequest.Builder createRequest(HTTP_METHOD method, final String urlString,
			final Map<String, String> parameters, final Map<String, String> headers,
			HttpRequest.BodyPublisher publisher) throws G2GClientException {

		// Create logger
		Logger logger = LoggerFactory.getLogger(RESTHelper.class);

		// Append parameters
		String modifiedURL = String.format("%s?%s", urlString, ParameterStringBuilder.getParamsString(parameters));

		// Make uri
		URI url;
		try {
			url = new URI(modifiedURL);
		} catch (URISyntaxException e) {
			throw new G2GClientException(String.format("A URL could not be created for %s", modifiedURL), e, logger);
		}

		// Create request
		Builder requestBuilder = HttpRequest.newBuilder();
		method.setMethod(requestBuilder, publisher);
		requestBuilder.uri(url);

		if (!CommonUtils.isNullOrEmpty(headers)) {
			// Set headers
			for (Map.Entry<String, String> header : headers.entrySet()) {
				requestBuilder.setHeader(header.getKey(), header.getValue());
			}
		}
		return requestBuilder;

	}

	/**
	 * Build {@code clientBuilder} and sends {@code request}
	 * 
	 * <p>
	 * 
	 * 
	 * @param <J>
	 * @param <T>
	 * @param request
	 * @param clientBuilder
	 * @param bodyHandler
	 * @param response
	 * @return
	 */
	private static <J, T> J excuteRequest(HttpRequest request, HttpClient.Builder clientBuilder,
			ErrorBodyHandler<J, String> bodyHandler, RESTResponse<T> response)
			throws G2GRestException, G2GClientException {

		// Build client
		HttpClient client = clientBuilder.build();

		// Execute
		try {

			HttpResponse<Response<J, String>> httpResponse = client.send(request, bodyHandler);

			int status = httpResponse.statusCode();
			response.setResponseHeaders(httpResponse.headers());
			if (status <= 299) {
				return (httpResponse.body().response());
			} else {
				String errorReason;

				errorReason = httpResponse.body().error();
				if (status != 200) {
					throw new G2GRestException(status, errorReason);
				}

			}

		} catch (InterruptedException e) {
			throw new G2GClientException(
					String.format("%s request to %s was interepted", request.method(), request.uri().toString()), e);
		} catch (IOException e) {
			throw new G2GClientException(
					String.format("%s request to %s encountered an I/O error when sending or receiving",
							request.method(), request.uri().toString()),
					e);
		}

		return null;
	}

	/**
	 * Does a get request and returns the string or byte[] output.
	 * <p>
	 * 
	 * @param urlString
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws
	 */
	private static <T> RESTResponse<T> doGetInternal(final String urlString, final Map<String, String> parameters,
			final Map<String, String> headers, final boolean asBytes) throws G2GClientException, G2GRestException {

		// Create logger
		Logger logger = LoggerFactory.getLogger(RESTHelper.class);

		// create response object
		RESTResponse<T> response = new RESTResponse<T>();

		// Create request
		Builder requestBuilder = createRequest(HTTP_METHOD.GET, urlString, parameters, headers,
				HttpRequest.BodyPublishers.noBody());

		// Build request
		HttpRequest request = requestBuilder.build();

		System.out.println("Make a " + request.method() + " request to " + request.uri().toString());
		logger.info("Make a {} request to {}", request.method(), request.uri().toString());

		// Create client
		HttpClient.Builder clientBuilder = HttpClient.newBuilder();

		// Execute
		if (asBytes) {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofByteArray(), BodyHandlers.ofString()), response));
			return response;

		} else {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofString(), BodyHandlers.ofString()), response));
			return response;
		}

	}

	private static <T> RESTResponse<T> doPutInternal(final Map<String, String> body, String urlString,
			final Map<String, String> parameters, final Map<String, String> headers, final boolean asBytes)
			throws G2GClientException, G2GRestException {

		// Create logger
		Logger logger = LoggerFactory.getLogger(RESTHelper.class);

		// create response object
		RESTResponse<T> response = new RESTResponse<T>();

		// Create a map we can change
		Map<String, String> headersWithChanges = Maps.newHashMap(headers);

		// Create Publisher
		HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
		if (!CommonUtils.isNullOrEmpty(body)) {
			if (!headersWithChanges.containsKey("Content-Type")) {
				headersWithChanges.put("Content-Type", "application/json");
			}
			// convert map to JSON String
			Gson gsonObj = new Gson();
			publisher = HttpRequest.BodyPublishers.ofString(gsonObj.toJson(body));
		}

		// Create request
		Builder requestBuilder = createRequest(HTTP_METHOD.PUT, urlString, parameters, headersWithChanges, publisher);

		// Build request
		HttpRequest request = requestBuilder.build();

		System.out.println("Make a " + request.method() + " request to " + request.uri().toString());
		logger.info("Make a {} request to {}", request.method(), request.uri().toString());

		// Create client
		HttpClient.Builder clientBuilder = HttpClient.newBuilder();

		// Execute
		if (asBytes) {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofByteArray(), BodyHandlers.ofString()), response));
			return response;

		} else {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofString(), BodyHandlers.ofString()), response));
			return response;
		}
	}

	private static <T> RESTResponse<T> doPostInternal(final Map<String, String> body, String urlString,
			final Map<String, String> parameters, final Map<String, String> headers, final boolean asBytes)
			throws G2GClientException, G2GRestException {

		// convert map to JSON String
		Gson gsonObj = new Gson();
		return doPostInternal(gsonObj.toJson(body), urlString, parameters, headers, asBytes);

	}

	private static <T> RESTResponse<T> doPostInternal(final String body, String urlString,
			final Map<String, String> parameters, final Map<String, String> headers, final boolean asBytes)
			throws G2GClientException, G2GRestException {
		// Create logger
		Logger logger = LoggerFactory.getLogger(RESTHelper.class);

		// create response object
		RESTResponse<T> response = new RESTResponse<T>();

		// Create a map we can change
		Map<String, String> headersWithChanges = Maps.newHashMap(headers);

		// Create Publisher
		HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
		if (!Strings.isNullOrEmpty(body)) {
			if (!headersWithChanges.containsKey("Content-Type")) {
				headersWithChanges.put("Content-Type", "application/json");
			}
			publisher = HttpRequest.BodyPublishers.ofString(body);
		}

		// Create request
		Builder requestBuilder = createRequest(HTTP_METHOD.POST, urlString, parameters, headersWithChanges, publisher);

		// Build request
		HttpRequest request = requestBuilder.build();

		System.out.println("Make a " + request.method() + " request to " + request.uri().toString());
		logger.info(String.format("Make a {} request to %s", request.method(), request.uri().toString()));

		// Create client
		HttpClient.Builder clientBuilder = HttpClient.newBuilder();

		// Execute
		if (asBytes) {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofByteArray(), BodyHandlers.ofString()), response));
			return response;

		} else {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofString(), BodyHandlers.ofString()), response));
			return response;
		}
	}

	private static <T> RESTResponse<T> doPatchInternal(final Map<String, List<Map<String, String>>> body,
			String urlString, final Map<String, String> parameters, final Map<String, String> headers,
			final boolean asBytes) throws G2GClientException, G2GRestException {
		// Create logger
		Logger logger = LoggerFactory.getLogger(RESTHelper.class);

		// create response object
		RESTResponse<T> response = new RESTResponse<T>();

		// Create a map we can change
		Map<String, String> headersWithChanges = Maps.newHashMap(headers);

		// Create Publisher
		HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
		if (!CommonUtils.isNullOrEmpty(body)) {
			if (!headersWithChanges.containsKey("Content-Type")) {
				headersWithChanges.put("Content-Type", "application/json");
			}
			// convert map to JSON String
			Gson gsonObj = new Gson();
			publisher = HttpRequest.BodyPublishers.ofString(gsonObj.toJson(body));
			System.out.println(gsonObj.toJson(body));
		}

		// Create request
		Builder requestBuilder = createRequest(HTTP_METHOD.PATCH, urlString, parameters, headersWithChanges, publisher);

		// Build request
		HttpRequest request = requestBuilder.build();

		System.out.println("Make a " + request.method() + " request to " + request.uri().toString());
		logger.info(String.format("Make a %s request to %s", request.method(), request.uri().toString()));

		// Create client
		HttpClient.Builder clientBuilder = HttpClient.newBuilder();

		// Execute
		if (asBytes) {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofByteArray(), BodyHandlers.ofString()), response));
			return response;

		} else {
			response.setResponse(excuteRequest(request, clientBuilder,
					new ErrorBodyHandler<>(BodyHandlers.ofString(), BodyHandlers.ofString()), response));
			return response;
		}
	}

	enum HTTP_METHOD {
		GET {
			@Override
			protected Builder setMethod(Builder requestBuilder, HttpRequest.BodyPublisher publisher) {

				return requestBuilder.GET();
			}
		},
		POST {
			@Override
			protected Builder setMethod(Builder requestBuilder, HttpRequest.BodyPublisher publisher) {
				return requestBuilder.POST(publisher);
			}
		},
		PUT {
			@Override
			protected Builder setMethod(Builder requestBuilder, HttpRequest.BodyPublisher publisher) {
				return requestBuilder.PUT(publisher);
			}
		},
		PATCH {
			@Override
			protected Builder setMethod(Builder requestBuilder, HttpRequest.BodyPublisher publisher) {

				return requestBuilder.method("PATCH", publisher);
			}
		},
		DELETE {
			@Override
			protected Builder setMethod(Builder requestBuilder, HttpRequest.BodyPublisher publisher) {
				return requestBuilder.DELETE();
			}
		};

		protected abstract Builder setMethod(Builder requestBuilder, HttpRequest.BodyPublisher publisher);
	}
}

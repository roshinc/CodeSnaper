/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.net.UrlEscapers;

import gov.nystax.nimbus.tools.get2git.domain.GitConfig;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GClientException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions.G2GRestException;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.response.RESTResponse;
import gov.nystax.nimbus.tools.get2git.remote.gitlab.api.util.RESTHelper;

/**
 * @author t63606
 *
 */
public abstract class AbstractGitlabApi<T> {
	private static String API_URL_FORMAT = "%s/api/v4/";

	private String accessToken;
	private String baseURL;

	private GitConfig config;

	protected final Logger logger;

	/**
	 * Parameters that needs to included with every request to the API.
	 */
	protected Map<String, String> gitlabParameters;
	/**
	 * Headers that needs to included with every request to the API.
	 */
	protected Map<String, String> gitlabHeaders;

	public AbstractGitlabApi(GitConfig config) {

		// Init logger
		logger = LoggerFactory.getLogger(this.getClass());

		// Set base URL
		this.accessToken = config.getAccessToken();
		this.baseURL = String.format(API_URL_FORMAT, config.getRemoteUrl().getBaseURL());

		Preconditions.checkNotNull(accessToken);
		Preconditions.checkNotNull(baseURL);

		gitlabParameters = new HashMap<>();

		gitlabHeaders = new HashMap<>();
		gitlabHeaders.put("PRIVATE-TOKEN", this.accessToken);

		this.config = config;
	}

	/**
	 * Does a get request to GitLab and returns the string output.
	 * 
	 * @param uriAPIName
	 * @param uriPath    un-escaped and without a leading slash.
	 * @param parameters
	 * @param headers
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	protected RESTResponse<T> doGetToGitLabInternal(final String uriAPIName, final String uriPath,
			final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		// Append parameters
		Map<String, String> newParameters = new HashMap<>(gitlabParameters);
		if (parameters != null)
			newParameters.putAll(parameters);

		// Append headers
		Map<String, String> newHeaders = new HashMap<>(gitlabHeaders);
		if (headers != null)
			newHeaders.putAll(headers);

		String gitlabURI = this.baseURL + uriAPIName + UrlEscapers.urlPathSegmentEscaper().escape(uriPath);
		RESTResponse<T> response = RESTHelper.doGet(gitlabURI, newParameters, newHeaders);

		// LoggerFactory.getLogger(this.getClass()).debug(String.format("GitLabHelper.doGetToGitLab:
		// %s", nextLink));
		return response;

	}

	protected RESTResponse<T> doPutToGitLabInternal(final Map<String, String> body, String uriAPIName,
			final String uriPath, final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		// Append parameters
		Map<String, String> newParameters = new HashMap<>(gitlabParameters);
		if (parameters != null)
			newParameters.putAll(parameters);

		// Append headers
		Map<String, String> newHeaders = new HashMap<>(gitlabHeaders);
		if (headers != null)
			newHeaders.putAll(headers);

		String gitlabURI = this.baseURL + uriAPIName + UrlEscapers.urlPathSegmentEscaper().escape(uriPath);
		RESTResponse<T> response = RESTHelper.doPut(body, gitlabURI, newParameters, newHeaders);

		// LoggerFactory.getLogger(this.getClass()).debug(String.format("GitLabHelper.doGetToGitLab:
		// %s", nextLink));
		return response;
	}

	protected RESTResponse<T> doPostToGitLabInternal(final Map<String, String> body, String uriAPIName,
			final String uriPath, final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		// Append parameters
		Map<String, String> newParameters = new HashMap<>(gitlabParameters);
		if (parameters != null)
			newParameters.putAll(parameters);

		// Append headers
		Map<String, String> newHeaders = new HashMap<>(gitlabHeaders);
		if (headers != null)
			newHeaders.putAll(headers);

		String gitlabURI = this.baseURL + uriAPIName + UrlEscapers.urlPathSegmentEscaper().escape(uriPath);
		RESTResponse<T> response = RESTHelper.doPost(body, gitlabURI, newParameters, newHeaders);

		// LoggerFactory.getLogger(this.getClass()).debug(String.format("GitLabHelper.doGetToGitLab:
		// %s", nextLink));
		return response;
	}

	protected RESTResponse<T> doPostToGitLabInternal(final String body, String uriAPIName, final String uriPath,
			final Map<String, String> parameters, final Map<String, String> headers)
			throws G2GClientException, G2GRestException {
		// Append parameters
		Map<String, String> newParameters = new HashMap<>(gitlabParameters);
		if (parameters != null)
			newParameters.putAll(parameters);

		// Append headers
		Map<String, String> newHeaders = new HashMap<>(gitlabHeaders);
		if (headers != null)
			newHeaders.putAll(headers);

		String gitlabURI = this.baseURL + uriAPIName + UrlEscapers.urlPathSegmentEscaper().escape(uriPath);
		RESTResponse<T> response = RESTHelper.doPost(body, gitlabURI, newParameters, newHeaders);

		// LoggerFactory.getLogger(this.getClass()).debug(String.format("GitLabHelper.doGetToGitLab:
		// %s", nextLink));
		return response;
	}

	protected RESTResponse<T> doPatchToGitLabInternal(final Map<String, List<Map<String, String>>> body,
			String uriAPIName, final String uriPath, final Map<String, String> parameters,
			final Map<String, String> headers) throws G2GClientException, G2GRestException {
		// Append parameters
		Map<String, String> newParameters = new HashMap<>(gitlabParameters);
		if (parameters != null)
			newParameters.putAll(parameters);

		// Append headers
		Map<String, String> newHeaders = new HashMap<>(gitlabHeaders);
		if (headers != null)
			newHeaders.putAll(headers);

		String gitlabURI = this.baseURL + uriAPIName + UrlEscapers.urlPathSegmentEscaper().escape(uriPath);
		RESTResponse<T> response = RESTHelper.doPatch(body, gitlabURI, newParameters, newHeaders);

		// LoggerFactory.getLogger(this.getClass()).debug(String.format("GitLabHelper.doGetToGitLab:
		// %s", nextLink));
		return response;
	}

	protected GitConfig getConfig() {
		return this.config;
	}

}

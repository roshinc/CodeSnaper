package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.response;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.gson.Gson;

public class RESTResponse<T> {

	private String response;

	private byte[] responseAsBytes;
	private boolean asBytes = false;

	private HttpHeaders responseHeaders;

	public String getResponse() {
		if (asBytes) {
			return responseAsBytes.toString();
		}
		return response;
	}

	public byte[] getBytesResponse() {
		return responseAsBytes;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public T getResponseAs(Class<T> classOfT) {
		T response = null;

		if (!Strings.isNullOrEmpty(this.response)) {
			Gson gson = new Gson();
			response = gson.fromJson(this.response, classOfT);
		}
		return response;
	}

	public void setResponse(byte[] responseAsBytes) {
		this.responseAsBytes = responseAsBytes;
		this.asBytes = true;
	}

	public HttpHeaders getResponseHeaders() {
		return responseHeaders;
	}

	public void setResponseHeaders(HttpHeaders httpHeaders) {
		this.responseHeaders = httpHeaders;
	}

	public void setResponseHeaders(Map<String, List<String>> headerFields) {
		// TODO Auto-generated method stub

	}
}

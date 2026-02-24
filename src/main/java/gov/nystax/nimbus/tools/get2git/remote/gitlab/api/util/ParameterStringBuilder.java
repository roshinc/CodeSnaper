package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.util;

import java.util.Map;

import com.google.common.net.UrlEscapers;

public class ParameterStringBuilder {
	/**
	 * Creates a String in the format of key=value&key=value&.. for the entries of
	 * {@code params}
	 * 
	 * <p>
	 * 
	 * The values are escaped {@linkplain UrlEscapers#urlPathSegmentEscaper()}
	 * 
	 * @param params {@linkplain Map} the params, null is allowed.
	 * @return
	 */
	public static String getParamsString(Map<String, String> params) {
		if (CommonUtils.isNullOrEmpty(params))
			return CommonUtils.EMPTY_STRING;
		StringBuilder result = new StringBuilder();

		for (Map.Entry<String, String> entry : params.entrySet()) {
			result.append(UrlEscapers.urlPathSegmentEscaper().escape(entry.getKey()));
			result.append("=");
			result.append(UrlEscapers.urlPathSegmentEscaper().escape(entry.getValue()));
			result.append("&");
		}

		String resultString = result.toString();
		return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
	}

}
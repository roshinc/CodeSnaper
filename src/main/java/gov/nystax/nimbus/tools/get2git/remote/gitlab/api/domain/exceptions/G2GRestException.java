/**
 * 
 */
package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain.exceptions;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gov.nystax.nimbus.tools.get2git.domain.exceptions.G2GException;

/**
 * @author t63606
 *
 */
public class G2GRestException extends G2GException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private int code;
	private String message;
	private Optional<String> parsedRESTMessage;

	public G2GRestException(int code, String message) {
		super(String.format("Error Code: %d, Reason: {%s}", code, message));
		this.code = code;
		this.message = message;
		this.parsedRESTMessage = parseMessage(this.message);
	}

	private Optional<String> parseMessage(String message) {
		try {
			JsonElement elem = JsonParser.parseString(message);
			JsonObject root = elem.getAsJsonObject();
			if (root != null && !root.isJsonNull()) {
				JsonElement parsedMsg = root.get("message");
				if (parsedMsg != null && !parsedMsg.isJsonNull()) {
					return Optional.ofNullable(parsedMsg.getAsString());
				}
			}

			return Optional.empty();
		} catch (Exception e) {
			return Optional.empty();
		}

	}

	public int getCode() {
		return code;
	}

	public String getRESTMessage() {
		return message;
	}

	public Optional<String> getParsedRESTMessage() {
		return parsedRESTMessage;
	}

}

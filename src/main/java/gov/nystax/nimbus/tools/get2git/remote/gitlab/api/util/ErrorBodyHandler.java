package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.util;

import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.ResponseInfo;

record Response<R, T> (R response, T error) {
}

public class ErrorBodyHandler<R, T> implements BodyHandler<Response<R, T>> {
	final BodyHandler<R> responseHandler;
	final BodyHandler<T> errorHandler;

	public ErrorBodyHandler(BodyHandler<R> responseHandler, BodyHandler<T> errorHandler) {
		this.responseHandler = responseHandler;
		this.errorHandler = errorHandler;
	}

	@Override
	public BodySubscriber<Response<R, T>> apply(ResponseInfo responseInfo) {
		if (responseInfo.statusCode() == 200 || responseInfo.statusCode() == 201) {
			return BodySubscribers.mapping(responseHandler.apply(responseInfo), (r) -> new Response<>(r, null));
		} else {
			return BodySubscribers.mapping(errorHandler.apply(responseInfo), (t) -> new Response<>(null, t));
		}
	}
}

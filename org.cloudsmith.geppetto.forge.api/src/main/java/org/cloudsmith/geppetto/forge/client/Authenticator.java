/**
 * Copyright (c) 2012 Cloudsmith Inc. and other contributors, as listed below.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Cloudsmith
 * 
 */
package org.cloudsmith.geppetto.forge.client;

import java.io.IOException;

/**
 * The authenticator is responsible for authenticating the user using username and password.
 * In response, it expects a string with valid scopes and a token that can be used for the
 * reminder of the session.
 */
public interface Authenticator {
	/**
	 * An interface that captures the authentication response.
	 */
	interface AuthResponse {
		String getScopes();

		String getToken();
	}

	/**
	 * Authenticate the user.
	 * 
	 * @param user
	 *            The username that identifies the user
	 * @param password
	 *            The users password
	 * @return An authentication response
	 * @throws IOException
	 *             when the authentication did not succeed
	 */
	AuthResponse authenticate(String user, String password) throws IOException;
}

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
package org.cloudsmith.geppetto.forge.v2.model;

import org.cloudsmith.geppetto.forge.model.Entity;

import com.google.gson.annotations.Expose;

/**
 * This represents the link entity from a series of links. This is loosely
 * based on HAL: http://stateless.co/hal_specification.html, however, only
 * href is exposed for now for now until we see a need for the other parameters.
 */
public class HalLink extends Entity {
	@Expose
	private String href;

	/**
	 * @return the href
	 */
	public String getHref() {
		return href;
	}
}

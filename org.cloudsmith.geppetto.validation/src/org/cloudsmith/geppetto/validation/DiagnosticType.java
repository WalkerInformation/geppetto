/**
 * Copyright (c) 2013 Cloudsmith Inc. and other contributors, as listed below.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Cloudsmith
 * 
 */
package org.cloudsmith.geppetto.validation;

public enum DiagnosticType {
	/**
	 * A problem of unknown origin - typically indicates an internal error where the code was not set.
	 */
	UNKNOWN(0),

	/**
	 * A general diagnostic emitted by geppetto (as oppose to the more specific GEPPETTO_SYNTAX).
	 */
	GEPPETTO(1),

	/**
	 * A syntax error found by Geppetto.
	 */
	GEPPETTO_SYNTAX(2),

	/**
	 * A diagnostic found by the catalog producer (running puppet), where the error was reported as "Could not parse....".
	 */
	CATALOG_PARSER(3),

	/**
	 * A general diagnostic prouced by the catalog validator (runnning puppet).
	 */
	CATALOG(4),

	/**
	 * A diagnostic produced by the forge compliance/validator.
	 */
	FORGE(5),

	/**
	 * Used to report hard errors (typically) relating to the org.cloudsmith.geppetto.validation service itself or the environment where it runs.
	 */
	INTERNAL_ERROR(6),
	
	RUBY_SYNTAX(7),
	
	RUBY(8),
	
	/**
	 * Used to report issues generated by the puppet-lint program
	 */
	PUPPET_LINT(9),
	
	/**
	 * Used to report issues generated by the module packaging logic
	 */
	PACKAGE(10),
	
	/**
	 * Used to report issues generated by the module publisher logic
	 */
	PUBLISHER(11);

	private final int code;

	
	private DiagnosticType(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
	
	public static DiagnosticType getByCode(int code) {
		for(DiagnosticType vsdCode : values()) {
			if(vsdCode.getCode() == code)
				return vsdCode;
		}
		throw new IllegalArgumentException();
	}
}

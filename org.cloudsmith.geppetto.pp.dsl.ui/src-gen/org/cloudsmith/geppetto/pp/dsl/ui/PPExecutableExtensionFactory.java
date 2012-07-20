/*
 * generated by Xtext
 */
package org.cloudsmith.geppetto.pp.dsl.ui;

import org.eclipse.xtext.ui.guice.AbstractGuiceAwareExecutableExtensionFactory;
import org.osgi.framework.Bundle;

import com.google.inject.Injector;

import org.cloudsmith.geppetto.pp.dsl.ui.internal.PPActivator;

/**
 * This class was generated. Customizations should only happen in a newly
 * introduced subclass. 
 */
public class PPExecutableExtensionFactory extends AbstractGuiceAwareExecutableExtensionFactory {

	@Override
	protected Bundle getBundle() {
		return PPActivator.getInstance().getBundle();
	}
	
	@Override
	protected Injector getInjector() {
		return PPActivator.getInstance().getInjector(PPActivator.ORG_CLOUDSMITH_GEPPETTO_PP_DSL_PP);
	}
	
}
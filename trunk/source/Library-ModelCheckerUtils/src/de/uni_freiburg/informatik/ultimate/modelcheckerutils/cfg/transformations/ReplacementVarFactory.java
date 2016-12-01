/*
 * Copyright (C) 2014-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 * 
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

/**
 * Factory for constructing ReplacementVars ensures that for each defining
 * Term exactly one ReplacementVar is constructed.
 * @author Matthias Heizmann
 *
 */
public class ReplacementVarFactory {
	
	private final ManagedScript mMgdScript;
	private final IIcfgSymbolTable mIIcfgSymbolTable;
	private final Map<Term, ReplacementVar> mRepVarMapping = new HashMap<>();
	private final Map<String, TermVariable> mAuxVarMapping = new HashMap<>();

	public ReplacementVarFactory(final ManagedScript mgdScript, final IIcfgSymbolTable symbolTable) {
		super();
		mMgdScript = mgdScript;
		mIIcfgSymbolTable = symbolTable;
	}

	/**
	 * Get the ReplacementVar that is used as a replacement for the Term
	 * definition. Construct this ReplacementVar if it does not exist yet.
	 */
	public ReplacementVar getOrConstuctReplacementVar(final Term definition) {
		ReplacementVar repVar = mRepVarMapping.get(definition);
		if (repVar == null) {
			analyzeDefinition(definition);
			final String name = SmtUtils.removeSmtQuoteCharacters(definition.toString());
			final TermVariable tv = mMgdScript.constructFreshTermVariable(name, definition.getSort());
			repVar = new ReplacementVar(definition.toString(), definition, tv);
			mRepVarMapping.put(definition, repVar);
		}
		return repVar;
	}


	/**
	 * Construct and return a unique TermVariable with the given name.
	 */
	public TermVariable getOrConstructAuxVar(final String name, final Sort sort) {
		TermVariable auxVar = mAuxVarMapping.get(name);
		if (auxVar == null) {
			auxVar = mMgdScript.constructFreshTermVariable(SmtUtils.removeSmtQuoteCharacters(name), sort);
			mAuxVarMapping.put(name, auxVar);
		} else {
			if (sort != auxVar.getSort()) {
				throw new AssertionError("cannot construct auxVars with same name and different sort");
			}
		}
		return auxVar;
	}

	
	
	private void analyzeDefinition(final Term definition) {
		final Set<Class> kinds = new HashSet<>();
		for (final TermVariable tv : definition.getFreeVars()) {
//			m
		}
	}
	
}

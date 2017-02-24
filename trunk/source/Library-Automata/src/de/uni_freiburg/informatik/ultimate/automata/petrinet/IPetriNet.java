/*
 * Copyright (C) 2011-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.petrinet;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.IAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.visualization.PetriNetToUltimateModel;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;

/**
 * General Petri net interface.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <S>
 *            symbols type
 * @param <C>
 *            place content type
 */
public interface IPetriNet<S, C> extends IAutomaton<S, C> {
	/**
	 * @return The places.
	 */
	Collection<Place<S, C>> getPlaces();

	/**
	 * @return The transitions.
	 */
	Collection<ITransition<S, C>> getTransitions();

	/**
	 * @return The initial marking.
	 */
	Marking<S, C> getInitialMarking();

	/**
	 * @return The accepting markings.
	 */
	Collection<Collection<Place<S, C>>> getAcceptingMarkings();

	/**
	 * @param marking
	 *            A marking.
	 * @return {@code true} iff the marking is accepting.
	 */
	boolean isAccepting(Marking<S, C> marking);

	/**
	 * @param word
	 *            A word.
	 * @return {@code true} iff the word is accepted.
	 */
	boolean accepts(Word<S> word);

	@Override
	default IElement transformToUltimateModel(final AutomataLibraryServices services)
			throws AutomataOperationCanceledException {
		return new PetriNetToUltimateModel<S, C>().transformToUltimateModel(this);
	}
}

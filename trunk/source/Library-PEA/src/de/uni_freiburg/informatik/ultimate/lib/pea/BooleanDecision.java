/*
 *
 * This file is part of the PEA tool set
 *
 * The PEA tool set is a collection of tools for
 * Phase Event Automata (PEA). See
 * http://csd.informatik.uni-oldenburg.de/projects/peatools.html
 * for more information.
 *
 * Copyright (C) 2005-2006, Department for Computing Science,
 *                          University of Oldenburg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package de.uni_freiburg.informatik.ultimate.lib.pea;

import java.util.Vector;

/**
 * BooleanDecision represents a simple boolean statement. It shall not be used
 * for arithmetical expressions like a < b + c anymore. In those cases use
 * RelationDecision instead.
 *
 * @author hoenicke
 * @see de.uni_freiburg.informatik.ultimate.lib.pea.RelationDecision
 */
public class BooleanDecision extends Decision {
    static Vector<String> allVars = new Vector<>();
    public static final String PRIME = "'";
    String var;
    int globalIdx = -1;

    public BooleanDecision(final String v) {
        globalIdx = allVars.indexOf(v);

        if (globalIdx < 0) {
            allVars.add(v);
            globalIdx = allVars.indexOf(v);
        }

        var = v;
    }

    /**
     * Create an boolean constraint.
     * @param var the condition that must hold.
     */
    public static CDD create(final String var) {
        return CDD.create(new BooleanDecision(var), CDD.trueChilds);
    }

    @Override
	public boolean equals(final Object o) {
        if (!(o instanceof BooleanDecision)) {
            return false;
        }

        return var.equals(((BooleanDecision) o).var);
    }

    @Override
	public int hashCode() {
        return var.hashCode();
    }

    @Override
	public int compareTo(final Object o) {
        if (!(o instanceof BooleanDecision)) {
            return 1;
        }

        return var.compareTo(((BooleanDecision) o).var);
    }

    /**
     * @return Returns the var.
     */
    @Override
	public String getVar() {
        return var;
    }

    @Override
	public String toString(final int child) {
        return (child == 0) ? var : ("!" + var);
    }

    @Override
	public String toSmtString(final int child) {
        return toSmtString(child, -1);
    }

    @Override
	public String toSmtString(final int child, final int index) {
        if (index < 0) {
            return (child == 0) ? ("(var_h_" + Math.abs(var.hashCode()) + ")")
                                : ("(not var_h_" + Math.abs(var.hashCode()) +
            ")");
        }
		return (child == 0)
			? ("(var_h_" + Math.abs(var.hashCode()) + "_" + index + ")")
			: ("(not var_h_" + Math.abs(var.hashCode()) + "_" + index + ")");
    }

    @Override
	public String toTexString(final int child) {
        return (child == 0) ? var : (" \\neg " + var);
    }

    @Override
	public String toUppaalString(final int child) {
        //return child == 0 ? var : " \\neg " + var;
        return "true";
    }

    @Override
	public String toUppaalStringDOM(final int child) {
        return "true";
    }

    private Decision primeCache;
    @Override
    public Decision prime() {
    	if (primeCache != null) {
			return primeCache;
		}
        final String decision = var.replaceAll("([a-zA-Z_])(\\w*)",
                "$1$2" + BooleanDecision.PRIME);

        primeCache = new BooleanDecision(decision);
        return primeCache;
    }

    //by Ami
    @Override
    public Decision unprime() {
        final String result = var.replaceAll("([a-zA-Z_])(\\w*)" +
                BooleanDecision.PRIME, "$1$2"); // SR 2010-08-02

        return (new BooleanDecision(result));
    }
}

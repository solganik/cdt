/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    John Camelon (IBM Rational Software) - Initial API and implementation
 *    Markus Schorn (Wind River Systems)
 *    Yuan Zhang / Beth Tibbitts (IBM Research)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser.c;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.c.ICASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.parser.util.ArrayUtil;

/**
 * Implementation for C composite specifiers.
 */
public class CASTCompositeTypeSpecifier extends CASTBaseDeclSpecifier implements
        ICASTCompositeTypeSpecifier {

    private int key;
    private IASTName name;


    public CASTCompositeTypeSpecifier() {
	}

	public CASTCompositeTypeSpecifier(int key, IASTName name) {
		this.key = key;
		setName(name);
	}
    
	public CASTCompositeTypeSpecifier copy() {
		CASTCompositeTypeSpecifier copy = new CASTCompositeTypeSpecifier();
		copyCompositeTypeSpecifier(copy);
		return copy;
	}
	
	protected void copyCompositeTypeSpecifier(CASTCompositeTypeSpecifier copy) {
		copyBaseDeclSpec(copy);
		copy.setKey(key);
		copy.setName(name == null ? null : name.copy());
		for(IASTDeclaration member : getMembers())
			copy.addMemberDeclaration(member == null ? null : member.copy());	
	}
	
	
    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        assertNotFrozen();
        this.key = key;
    }

    public IASTName getName() {
        return name;
    }
    
    public void setName(IASTName name) {
        assertNotFrozen();
        this.name = name;
        if (name != null) {
			name.setParent(this);
			name.setPropertyInParent(TYPE_NAME);
		}
    }

    
    private IASTDeclaration [] declarations = null;
    private int declarationsPos=-1;
    private IScope scope = null;
    

    public IASTDeclaration [] getMembers() {
        if( declarations == null ) return IASTDeclaration.EMPTY_DECLARATION_ARRAY;
        declarations = (IASTDeclaration[]) ArrayUtil.removeNullsAfter( IASTDeclaration.class, declarations, declarationsPos );
        return declarations;
    }


    public void addMemberDeclaration(IASTDeclaration declaration) {
        assertNotFrozen();
    	if (declaration != null) {
    		declaration.setParent(this);
    		declaration.setPropertyInParent(MEMBER_DECLARATION);
    		declarations = (IASTDeclaration[]) ArrayUtil.append( IASTDeclaration.class, declarations, ++declarationsPos, declaration );
    	}
    }
    
    public IScope getScope() {
        if( scope == null )
            scope = new CCompositeTypeScope( this );
        return scope;
    }

    @Override
	public boolean accept( ASTVisitor action ){
        if( action.shouldVisitDeclSpecifiers ){
		    switch( action.visit( this ) ){
	            case ASTVisitor.PROCESS_ABORT : return false;
	            case ASTVisitor.PROCESS_SKIP  : return true;
	            default : break;
	        }
		}
        if( name != null ) if( !name.accept( action ) ) return false;
           
        IASTDeclaration [] decls = getMembers();
        for( int i = 0; i < decls.length; i++ )
            if( !decls[i].accept( action ) ) return false;
            
        if( action.shouldVisitDeclSpecifiers ){
		    switch( action.leave( this ) ){
	            case ASTVisitor.PROCESS_ABORT : return false;
	            case ASTVisitor.PROCESS_SKIP  : return true;
	            default : break;
	        }
		}
        return true;
    }


	public int getRoleForName(IASTName n) {
		if( n == this.name )
			return r_definition;
		return r_unclear;
	}

	
}

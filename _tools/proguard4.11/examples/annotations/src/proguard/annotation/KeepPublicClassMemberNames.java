/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2007 Eric Lafortune (eric@graphics.cornell.edu)
 */
package proguard.annotation;

/**
 * This annotation specifies to keep all public class members of the annotated
 * class from being optimized or obfuscated as entry points.
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface KeepPublicClassMemberNames {}

package org.elder.sourcerer.subscription;

/**
 * Thrown when worker is currently consuming events and the run policy said to stop.
 */
public class RunPolicyPreventionException extends RuntimeException {
}

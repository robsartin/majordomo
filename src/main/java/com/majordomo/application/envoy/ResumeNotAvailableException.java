package com.majordomo.application.envoy;

/**
 * Thrown when the user's résumé cannot be used as grounding text (#349,
 * ADR-0028).
 *
 * <p>Generation must not proceed without it: a draft written from no source is
 * the ungrounded draft the ADR exists to prevent. The reason is carried rather
 * than flattened into one message because the three cases have three different
 * fixes — upload one, wait for it to be read, or replace it with something
 * readable — and telling someone the wrong one sends them looking in the wrong
 * place.
 */
public class ResumeNotAvailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why the résumé is unusable. */
    public enum Reason {

        /** No résumé has been attached to the user record. */
        NONE_UPLOADED("No résumé uploaded. Attach one to your account first."),

        /**
         * Uploaded, but the extraction sweep has not read it yet. It runs every
         * few minutes, so this resolves on its own.
         */
        NOT_YET_EXTRACTED("Your résumé has not been read yet — extraction runs every "
                + "few minutes. Try again shortly."),

        /** Read, but no usable text came out — a scan of a blank page, or a corrupt file. */
        UNREADABLE("No text could be read from your résumé. Try uploading a "
                + "text-based PDF rather than a scan or photograph.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        /**
         * Returns the message shown to the user.
         *
         * @return the explanation, including what to do about it
         */
        public String message() {
            return message;
        }
    }

    private final Reason reason;

    /**
     * Constructs the exception.
     *
     * @param reason why the résumé is unusable
     */
    public ResumeNotAvailableException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    /**
     * Returns why the résumé is unusable.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }
}

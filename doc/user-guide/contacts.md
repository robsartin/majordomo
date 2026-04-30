# Contacts

Contacts are the people you do business with — your HVAC tech, your
landscaper, the Honda dealership, your insurance broker. The
**Concierge** service manages them.

## The list page

`/contacts` shows every non-archived contact in your organization.

- **Search**: free-text query matches across name, organization
  (employer), and any email address.
- **Organization filter**: exact-match dropdown sourced from the
  distinct values seen in your contacts list.
- **+ New contact** button.
- Each row shows formatted name, employer, title, primary email, primary
  phone. Click the name to jump to detail.

## Adding a contact

The form fields:

| Field | Notes |
|---|---|
| **Display name** | Required. The friendly form ("Alice Example", "Acme Inc"). |
| **Given name / Family name** | Optional structured names. Used for vCard export. |
| **Organization** | Their employer or affiliation. |
| **Title** | Their job title. |
| **Emails** | One per line in the textarea. Each line is validated as a `local@domain.tld` format and capped at 254 characters (RFC 5321). |
| **Phones** | One per line. No format validation. |
| **URLs** | One per line. Stored as-is. |
| **Nicknames** | One per line. |
| **Notes** | Free-form. |

### Addresses sub-form

Below the simple fields, the **Addresses** section lets you add multiple
postal addresses with label / street / city / state / postal code /
country. Click **+ Add address** to append a blank row. To **delete** a
row on edit, clear all of its fields and save — entirely-blank rows are
dropped.

## The detail page

`/contacts/{id}` shows:

- **Header**: formatted name, organization + title, **Edit** button,
  **Download vCard** link (returns a `.vcf` file you can drop into
  Apple Contacts, Google Contacts, etc.).
- **Email / Phone / URL / Nickname** panels.
- **Addresses** panel: each labeled with WORK / HOME / etc.
- **Notes**.
- **Linked properties**: properties this contact is associated with,
  with **Unlink** buttons.

## Linking properties

Mirror of the property side: scroll to **Linked properties**, pick a
property + role + notes, click **+ Link property**. The picker excludes
properties already linked to this contact.

## Editing

Click **Edit** on the detail page; the form pre-populates with the
current values. Save replaces the contact's lists wholesale (so e.g.
removing a phone number means deleting that line from the Phones
textarea).

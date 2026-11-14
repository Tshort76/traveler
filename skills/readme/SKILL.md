---
name: readme
description: Create or rewrite a repository README as a concise, visual introduction to its purpose, domain concepts and key workflows. Use for README authoring or restructuring; preserve the audience and scope for narrow edits.
---

# README as an introduction

Give the reader a conceptual framework for what the product accomplishes and how its pieces fit together. Prefer at-a-glance explanations, real visuals and concise language to an operating manual. The reader should understand the product before learning its controls.

## Ground it in the repository

Read the current README, repository guidance, relevant documentation, models, navigation and workflow instructions. Use those sources to establish what exists; do not invent commands, screen names or capabilities. Keep valuable setup and operational detail reachable through existing docs when shortening the README. Move unique material into an appropriate document if it would otherwise be lost.

Identify the audience and the product's major surfaces: for example an app, CLI, library or assistant sessions. Give materially different surfaces separate sections. A self-explanatory app needs a visual introduction; an assistant command interface needs a workflow reference. Do not impose either section on a repository that lacks it.

## Choose the content

- Open with what the product helps someone accomplish, in one or two sentences.
- Introduce the small set of domain concepts the reader needs. Distinguish domain objects from exchange file types, commands and implementation classes. A Day is a concept; a daily patch is a file used to update one.
- Use a small relationship diagram when it clarifies the mental model. Mermaid works well in GitHub Markdown. Keep field-level models out of this overview.
- Explain the main workflows without enumerating every button or exception. Link detailed setup, administration and troubleshooting elsewhere.
- End with **Additional Documentation**, linking relevant setup, architecture, format/reference and build documentation. Preserve necessary license and project-specific information.

These are content priorities, not mandatory boilerplate headings. Omit sections that add no useful information.

## Show visual products

For an app or other graphical product, use actual screenshots to explain the major screens and the progression between them. A table of screen names is not a substitute. Prefer a small representative walkthrough to an exhaustive gallery.

Capture current screens using the repository's supported tooling and approved demo/fixture data. Follow its device and emulator rules; do not expose personal data or install on an owner's device without authorization. Never invent screenshots. If screenshots cannot be obtained, state the limitation and keep the prose modest rather than pretending to provide a visual tour.

Save screenshots in a tracked documentation directory and embed them at a readable, bounded size. Make each image clickable to its full-size version. Put a short caption directly beneath the corresponding image, explaining what the reader can understand or decide there. Do not place two screens' captions together in a paragraph that requires matching “left” and “right.”

For GitHub, paired images can use HTML table cells, each containing its image, a line break and a `<sub>` caption. This keeps smaller captions attached to their images without relying on stripped CSS:

```html
<table>
<tr>
<td width="50%" valign="top">
<a href="docs/screenshots/overview.png"><img src="docs/screenshots/overview.png" width="260" alt="Describe the visible screen"></a><br>
<sub><b>Overview.</b> Explain the decision this screen supports.</sub>
</td>
<td width="50%" valign="top">
<a href="docs/screenshots/detail.png"><img src="docs/screenshots/detail.png" width="260" alt="Describe the visible screen"></a><br>
<sub><b>Detail.</b> Explain how this view helps with the next step.</sub>
</td>
</tr>
</table>
```

Adapt sizes and grouping to the product. Inspect captured images before publishing; check that labels, captions and paths correspond. Stop any emulator started for the work.

## Make nonvisual workflows explicit

For assistant sessions, commands or file exchange, give each major workflow its own short subsection:

**Input:** what context, files or decisions the user supplies.

A concrete command or natural-language example.

**Output:** what comes back and where the user takes it next.

Distinguish exploring ideas from generating a deliverable, adding items from replacing a plan, and a partial patch from a full revision. Explain required context and file transport where users cannot infer them from a UI. For assistant projects, distinguish uploaded execution files, pasted project instructions and instructions fetched from a connected repository. Base this on the actual integration contract.

## Keep technical detail one link away

When the key models warrant explanation, add or update a separate data-model document and link it under Additional Documentation. Use a compact UML class diagram grounded in actual models. Show ownership and references accurately; explain concepts that are views rather than stored classes. Keep patch/exchange file types here and in their workflows. Avoid duplicating the full schema or architecture guide.

## Finish within scope

Check local links, command names and screenshot references. Run repository-required checks and follow its commit/publish rules; this skill grants no additional publication authority. Report changes to uploaded or pasted assistant-project files so the user knows exactly what needs refreshing. Do not redesign the app to make its README easier to write.

# Community data

Workouts people have shared from OpenFit, and AI results kept so each video is only read once.
The backend writes here through GitHub's API (each share, rating or new AI result is a commit);
anyone can read, fork or self-host it.

| Path | What |
| --- | --- |
| `programs.json` | Every shared workout: name, steps (speed km/h, incline %, seconds), YouTube id, rating total and count. |
| `votes/<id>.json` | One entry per voter, `{ voter: stars }`. Voters are keyed with a server-only secret, so they cannot be traced back to an install or an address. |
| `cache/analysis/<videoId>.json` | The AI's speed readings for a video (the workout is built from these). |
| `cache/facts20/<videoId>.json` | Facts about the place a video was filmed, for the virtual coach. |
| `cache/landmarks/<videoId>.json` | Landmarks along a video and when they come into view. |

A workout here is only a name, steps and a YouTube video id: no video, no personal data.

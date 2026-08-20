<p align="center">
  <img src="docs/expanda-banner.png" width="100%" alt="Expanda panda logo">
</p>

# Expanda

Expanda is a text expander for Android. It provides snippets, multiple templates, tags, dynamic tokens, text actions and an overlay with suggestions while you type.

## Download

Download the latest APK from [GitHub Releases](https://github.com/diegomarzaa/expanda/releases/latest). Expanda requires Android 8.0 or newer and access to Android's accessibility service.

## Motivation

I could not find a good open-source alternative that covered the workflow I wanted. I also wanted to learn how Android accessibility services, background execution, overlays and text editing work in a real app.

## Inspiration

I based the idea and much of the workflow on [Typing Hero](https://typinghero.app/), which was my main reference. I also took inspiration from [Expandroid](https://github.com/lochidev/Expandroid).

## Project status

This is the first public release. I built the codebase through vibe coding and focused on keeping the app functional. I have not reviewed most of the generated code in depth.

## Future plans

- Integrate with [Espanso](https://espanso.org/) and sync snippets across Android, Linux and Windows.
- Reduce overlay latency, battery use and unnecessary background work.
- Add safe sync conflict handling, clearer import previews and backup validation.
- Review and simplify the generated code before treating the app as stable.

## Contributing

Pull requests are welcome, especially fixes that improve reliability, simplify the code or document unclear parts.

## License

[MIT](LICENSE)

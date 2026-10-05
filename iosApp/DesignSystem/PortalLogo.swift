import SwiftUI

/// The brand's **portal logo** (`UI_SPEC.md` §1.2 `Brand/Portal logo`, Figma `16:13`, `DEC-139`): the
/// multi-tone green spiral, drawn from the app's asset catalog as a vector, so it is sharp at the
/// splash's 176 pt and at an empty state's or a failed portrait's mark size alike.
///
/// It is the one brand mark on iOS, as `ic_portal_mark` is on Android: the splash turns it, and the
/// empty states and a failed portrait show it at 40 %. It is decorative wherever it appears — the
/// surrounding view carries the meaning — so it is hidden from accessibility.
public struct PortalLogo: View {
    public init() {}

    public var body: some View {
        Image("PortalLogo")
            .resizable()
            .scaledToFit()
            .accessibilityHidden(true)
    }
}

#Preview("Portal logo") {
    PortalLogo()
        .frame(width: 176, height: 176)
        .padding()
        .background(MultiverseBrandColors.spaceBlack)
        .preferredColorScheme(.dark)
}

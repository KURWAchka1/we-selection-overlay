import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const canonical = 'WEaddon_fabric_26.2';
const screen = 'src/main/java/org/marshal/weaddon_lightposition/client/screen';
const assets = 'src/main/resources/assets/worldedit_selection_overlay';
const targets = [
  { name: 'WEaddon_lightposition', version: '26.1' },
  { name: 'WEaddon_neoforge_26.2', version: '26.2' },
  { name: 'WEaddon_neoforge_26.1', version: '26.1' },
  { name: 'WEaddon_neoforge_1.21.11', version: '1.21.11' },
  { name: 'WEaddon_fabric_1.21.11', version: 'yarn' }
];

const legacyPipeline = `RenderPipeline source = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id(outline ? "pipeline/ui_edge" : fragment.equals("core/ui_spectrum") ? "pipeline/ui_spectrum" : "pipeline/ui_surface"))
                .withVertexShader(source.getVertexShader())
                .withFragmentShader(id(fragment))
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withDepthTestFunction(source.getDepthTestFunction()).withDepthWrite(false)
                .withCull(false);
        source.getBlendFunction().ifPresent(builder::withBlend);
        for (var uniform : source.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        for (String sampler : source.getSamplers()) builder.withSampler(sampler);`;
const modern261Pipeline = `RenderPipeline source = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id(outline ? "pipeline/ui_edge" : fragment.equals("core/ui_spectrum") ? "pipeline/ui_spectrum" : "pipeline/ui_surface"))
                .withVertexShader(source.getVertexShader())
                .withFragmentShader(id(fragment))
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withColorTargetState(source.getColorTargetState())
                .withDepthStencilState(java.util.Optional.ofNullable(source.getDepthStencilState()))
                .withCull(false);
        for (var uniform : source.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        for (String sampler : source.getSamplers()) builder.withSampler(sampler);`;

for (const target of targets) {
  for (const file of ['UiStyle.java', 'UiMotion.java', 'UiChrome.java', 'UiButton.java', 'UiEditBox.java', 'UiScreen.java', 'UiPerformance.java', 'OverlaySettingsScreen.java']) {
    let code = await fs.readFile(path.join(root, canonical, screen, file), 'utf8');
    if (file === 'UiChrome.java' && target.version !== '26.2') {
      const start = code.indexOf('RenderPipeline source =');
      const end = code.indexOf('        if (outline) builder.withShaderDefine', start);
      code = code.slice(0, start) + (target.version === '26.1' ? modern261Pipeline : legacyPipeline) + '\n' + code.slice(end);
    }
    if (target.version !== '26.2') code = code.replaceAll('minecraft.gui.setScreen(', 'minecraft.setScreen(');
    if (target.version === '1.21.11') {
      code = code.replaceAll('GuiGraphicsExtractor', 'GuiGraphics')
        .replaceAll('extractRenderState', 'render').replaceAll('extractContents', 'renderContents')
        .replaceAll('extractBackground', 'renderBackground').replaceAll('extractBlurredBackground', 'renderBlurredBackground')
        .replaceAll('extractWidgetRenderState', 'renderWidget')
        .replaceAll('graphics.text(', 'graphics.drawString(').replaceAll('graphics.outline(', 'graphics.renderOutline(');
    }
    if (target.version === 'yarn') {
      code = code.replace('import net.minecraft.client.renderer.RenderPipelines;', 'import net.minecraft.client.gl.RenderPipelines;')
        .replaceAll('net.minecraft.client.Minecraft', 'net.minecraft.client.MinecraftClient')
        .replaceAll('Minecraft.getInstance()', 'MinecraftClient.getInstance()').replaceAll('MinecraftClient.getInstance().font', 'MinecraftClient.getInstance().textRenderer')
        .replaceAll('net.minecraft.client.gui.Font', 'net.minecraft.client.font.TextRenderer')
        .replaceAll('net.minecraft.client.gui.components.Tooltip', 'net.minecraft.client.gui.tooltip.Tooltip')
        .replaceAll('Tooltip.create(', 'Tooltip.of(')
        .replaceAll('net.minecraft.client.gui.components.Button', 'net.minecraft.client.gui.widget.ButtonWidget')
        .replaceAll('net.minecraft.client.gui.components.EditBox', 'net.minecraft.client.gui.widget.TextFieldWidget')
        .replace(/\bEditBox\b/g, 'TextFieldWidget').replaceAll('setBordered(', 'setDrawsBackground(')
        .replaceAll('setTextShadow(', 'setTextShadow(')
        .replaceAll('net.minecraft.client.gui.screens.Screen', 'net.minecraft.client.gui.screen.Screen')
        .replaceAll('net.minecraft.client.input.MouseButtonEvent', 'net.minecraft.client.gui.Click')
        .replaceAll('net.minecraft.network.chat.Component', 'net.minecraft.text.Text')
        .replaceAll('net.minecraft.network.chat.FontDescription', 'net.minecraft.text.StyleSpriteSource')
        .replaceAll('net.minecraft.resources.Identifier', 'net.minecraft.util.Identifier')
        .replaceAll('net.minecraft.util.FormattedCharSequence', 'net.minecraft.text.OrderedText')
        .replaceAll('GuiGraphicsExtractor', 'DrawContext').replaceAll('Component', 'Text')
        .replaceAll('FontDescription.Resource', 'StyleSpriteSource.Font').replaceAll('FontDescription', 'StyleSpriteSource')
        .replaceAll('FormattedCharSequence', 'OrderedText').replaceAll('Font font', 'TextRenderer font')
        .replaceAll('Identifier.fromNamespaceAndPath(', 'Identifier.of(')
        .replaceAll('extractRenderState', 'render').replaceAll('extractContents', 'drawIcon')
        .replaceAll('extractWidgetRenderState', 'renderWidget')
        .replaceAll('extractBackground', 'renderBackground').replaceAll('extractBlurredBackground', 'applyBlur')
        .replaceAll('fadeWidgets(', 'setWidgetAlpha(')
        .replaceAll('graphics.blit(', 'graphics.drawTexture(').replaceAll('graphics.blitSprite(', 'graphics.drawGuiTexture(').replaceAll('graphics.text(', 'graphics.drawText(')
        .replaceAll('graphics.pose()', 'graphics.getMatrices()')
        .replaceAll('graphics.outline(', 'graphics.drawStrokedRectangle(').replaceAll('.withStyle(', '.styled(')
        .replaceAll('font.width(', 'font.getWidth(').replaceAll('isHoveredOrFocused()', 'isSelected()')
        .replaceAll('addRenderableWidget(', 'addDrawableChild(')
        .replaceAll('minecraft.setScreen(', 'client.setScreen(').replaceAll('minecraft != null', 'client != null')
        .replaceAll('isPauseScreen()', 'shouldPause()').replaceAll('onClose()', 'close()')
        .replaceAll('super::onClose', 'super::close').replace(/\bButton\b/g, 'ButtonWidget')
        .replaceAll('Button.builder(', 'ButtonWidget.builder(').replaceAll('.bounds(', '.dimensions(')
        .replaceAll('Builder bounds(', 'Builder dimensions(').replaceAll('OnPress', 'PressAction')
        .replaceAll('DEFAULT_NARRATION', 'DEFAULT_NARRATION_SUPPLIER').replaceAll('MouseButtonEvent', 'Click');
      code = code.replaceAll('private Text ', 'private net.minecraft.text.Text ').replaceAll('final Text ', 'final net.minecraft.text.Text ')
        .replaceAll('Text source =', 'net.minecraft.text.Text source =')
        .replaceAll('styled(Text ', 'styled(net.minecraft.text.Text ').replaceAll('Builder(Text ', 'Builder(net.minecraft.text.Text ')
        .replaceAll('value, Text label', 'value, net.minecraft.text.Text label').replaceAll('tooltip(Text ', 'tooltip(net.minecraft.text.Text ')
        .replaceAll('Text text =', 'net.minecraft.text.Text text =');
      if (file === 'UiScreen.java') {
        code = code.replace('applyBlur(graphics);', 'graphics.applyBlur();')
          .replace('private final UiMotion motion', 'protected final net.minecraft.client.font.TextRenderer font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;\n    private final UiMotion motion');
      }
      if (file === 'OverlaySettingsScreen.java') code = code.replaceAll('button.setHeight(height);', 'button.setDimensionsAndPosition(width, height, x, y);');
    }
    await fs.writeFile(path.join(root, target.name, screen, file), code);
  }
  for (const file of ['shaders/core/ui_surface.fsh', 'shaders/core/ui_spectrum.fsh', 'font/ui.json', 'textures/gui/ui/surface.png.mcmeta']) {
    const output = path.join(root, target.name, assets, file);
    await fs.mkdir(path.dirname(output), { recursive: true });
    await fs.copyFile(path.join(root, canonical, assets, file), output);
  }
}
console.log('Synchronized the UI layer into all five other runtimes. Compile every target after mapping changes.');

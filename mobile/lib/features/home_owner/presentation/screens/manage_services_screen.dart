import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../notifiers/home_owner_notifier.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../notifiers/home_owner_notifier.dart';

class ManageServicesScreen extends ConsumerWidget {
  const ManageServicesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final services = ref.watch(homeOwnerNotifierProvider.select((s) => s.services));
    final loading  = ref.watch(homeOwnerNotifierProvider.select((s) => s.servicesLoading));

    return Scaffold(
      backgroundColor: AppColors.obsidian,
      appBar: AppBar(
        backgroundColor: AppColors.obsidian,
        title: Text('Servicios', style: AppTypography.headingMd),
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: const Icon(Icons.arrow_back_ios_rounded,
            size: 18, color: AppColors.white),
        ),
        actions: [
          GestureDetector(
            onTap: () => _showServiceSheet(context, ref, null),
            child: Container(
              margin: const EdgeInsets.only(right: AppSpacing.base),
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.md, vertical: AppSpacing.xs + 2),
              decoration: BoxDecoration(
                gradient: AppColors.goldGradient,
                borderRadius: AppRadius.rPill,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.add_rounded,
                    size: 14, color: AppColors.obsidian),
                  const SizedBox(width: 4),
                  Text('Nuevo', style: AppTypography.labelSm.copyWith(
                    color: AppColors.obsidian, fontWeight: FontWeight.w700)),
                ],
              ),
            ),
          ),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator(color: AppColors.gold))
          : services.isEmpty
              ? _EmptyServices(
                  onAdd: () => _showServiceSheet(context, ref, null))
              : ListView.separated(
                  padding: AppSpacing.screen,
                  itemCount: services.length,
                  separatorBuilder: (_, __) =>
                      const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (_, i) => _ServiceListTile(
                    service: services[i],
                    onEdit: () => _showServiceSheet(context, ref, services[i]),
                  ).animate().fadeIn(delay: Duration(milliseconds: 60 * i)),
                ),
    );
  }

  void _showServiceSheet(
    BuildContext context,
    WidgetRef ref,
    OwnerService? existing,
  ) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ProviderScope(
        parent: ProviderScope.containerOf(context),
        child: _ServiceFormSheet(existing: existing),
      ),
    );
  }
}

// ─── _ServiceListTile ────────────────────────────────────────────────────────

class _ServiceListTile extends StatelessWidget {
  final OwnerService service;
  final VoidCallback onEdit;

  const _ServiceListTile({required this.service, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onEdit,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Row(
          children: [
            // Icono de categoría
            Container(
              width: 44, height: 44,
              decoration: BoxDecoration(
                color: AppColors.goldSubtle,
                borderRadius: AppRadius.rSm,
              ),
              child: const Icon(
                Icons.content_cut_rounded,
                color: AppColors.gold, size: 20,
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(service.name,
                          style: AppTypography.headingSm),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.xs + 2, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.goldSubtle,
                          borderRadius: AppRadius.rPill,
                        ),
                        child: Text(service.category,
                          style: AppTypography.overline.copyWith(
                            color: AppColors.gold, fontSize: 8)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Text(service.priceLabel,
                        style: AppTypography.monoMd.copyWith(fontSize: 14)),
                      const SizedBox(width: AppSpacing.md),
                      Text(service.durationLabel,
                        style: AppTypography.monoSm.copyWith(
                          color: AppColors.ash)),
                      if (service.hasDeposit) ...[
                        const SizedBox(width: AppSpacing.md),
                        Row(children: [
                          const Icon(Icons.payments_outlined,
                            size: 11, color: AppColors.ash),
                          const SizedBox(width: 3),
                          Text('Depósito \$${service.depositAmount}',
                            style: AppTypography.monoSm.copyWith(
                              color: AppColors.ash, fontSize: 10)),
                        ]),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            const Icon(Icons.chevron_right_rounded,
              size: 18, color: AppColors.ash),
          ],
        ),
      ),
    );
  }
}

// ─── _ServiceFormSheet — Crear / Editar servicio ──────────────────────────────

class _ServiceFormSheet extends ConsumerStatefulWidget {
  final OwnerService? existing;
  const _ServiceFormSheet({this.existing});

  @override
  ConsumerState<_ServiceFormSheet> createState() => _ServiceFormSheetState();
}

class _ServiceFormSheetState extends ConsumerState<_ServiceFormSheet> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _priceCtrl;
  late final TextEditingController _durationCtrl;
  late final TextEditingController _depositCtrl;

  String _category    = 'CORTE';
  String _depositType = 'NONE';
  bool _isLoading     = false;

  static const _categories = ['CORTE','BARBA','CEJA','TRATAMIENTO','COMBO'];
  static const _depositTypes = ['NONE','FIXED','PERCENTAGE'];

  @override
  void initState() {
    super.initState();
    final s = widget.existing;
    _nameCtrl     = TextEditingController(text: s?.name ?? '');
    _priceCtrl    = TextEditingController(text: s?.basePrice ?? '');
    _durationCtrl = TextEditingController(text: s?.durationMinutes.toString() ?? '30');
    _depositCtrl  = TextEditingController(text: s?.depositAmount ?? '0');
    _category     = s?.category ?? 'CORTE';
    _depositType  = s?.depositType ?? 'NONE';
  }

  @override
  void dispose() {
    _nameCtrl.dispose(); _priceCtrl.dispose();
    _durationCtrl.dispose(); _depositCtrl.dispose();
    super.dispose();
  }

  bool get _isEditing => widget.existing != null;
  bool get _canSave   =>
      _nameCtrl.text.trim().isNotEmpty &&
      _priceCtrl.text.trim().isNotEmpty &&
      _durationCtrl.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).viewInsets.bottom +
        MediaQuery.of(context).padding.bottom;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rBottomSheet),
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40, height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill),
          ),
          Flexible(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(child: Text(
                        _isEditing ? 'Editar servicio' : 'Nuevo servicio',
                        style: AppTypography.displayMd.copyWith(fontSize: 20),
                      )),
                      GestureDetector(
                        onTap: () => Navigator.pop(context),
                        child: Container(
                          width: 32, height: 32,
                          decoration: BoxDecoration(
                            color: AppColors.slate, borderRadius: AppRadius.rSm),
                          child: const Icon(Icons.close_rounded,
                            size: 16, color: AppColors.silver),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: AppSpacing.xl),

                  // Nombre
                  AppTextField(
                    label: 'Nombre del servicio',
                    hint: 'Ej. Corte clásico',
                    controller: _nameCtrl,
                    textInputAction: TextInputAction.next,
                    onChanged: (_) => setState(() {}),
                  ),

                  const SizedBox(height: AppSpacing.md),

                  // Categoría — chips
                  Text('CATEGORÍA', style: AppTypography.overline.copyWith(
                    color: AppColors.ash)),
                  const SizedBox(height: AppSpacing.sm),
                  Wrap(
                    spacing: AppSpacing.sm,
                    runSpacing: AppSpacing.sm,
                    children: _categories.map((cat) => GestureDetector(
                      onTap: () => setState(() => _category = cat),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 150),
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.md, vertical: AppSpacing.xs + 2),
                        decoration: BoxDecoration(
                          color: _category == cat
                              ? AppColors.gold : AppColors.slate,
                          borderRadius: AppRadius.rPill,
                          border: Border.all(
                            color: _category == cat
                                ? AppColors.gold : AppColors.smoke),
                        ),
                        child: Text(cat, style: AppTypography.labelSm.copyWith(
                          color: _category == cat
                              ? AppColors.obsidian : AppColors.silver,
                          fontWeight: FontWeight.w600,
                        )),
                      ),
                    )).toList(),
                  ),

                  const SizedBox(height: AppSpacing.md),

                  // Precio y duración — lado a lado
                  Row(
                    children: [
                      Expanded(
                        child: AppTextField(
                          label: 'Precio (MXN)',
                          hint: '150.00',
                          controller: _priceCtrl,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          textInputAction: TextInputAction.next,
                          prefixIcon: const Icon(Icons.attach_money_rounded,
                            size: 18, color: AppColors.ash),
                          onChanged: (_) => setState(() {}),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: AppTextField(
                          label: 'Duración (min)',
                          hint: '30',
                          controller: _durationCtrl,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.next,
                          prefixIcon: const Icon(Icons.timer_outlined,
                            size: 18, color: AppColors.ash),
                          onChanged: (_) => setState(() {}),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: AppSpacing.md),

                  // Depósito
                  Text('DEPÓSITO', style: AppTypography.overline.copyWith(
                    color: AppColors.ash)),
                  const SizedBox(height: AppSpacing.sm),
                  Row(
                    children: [
                      ..._depositTypes.map((type) {
                        final label = switch (type) {
                          'NONE'       => 'Sin depósito',
                          'FIXED'      => 'Fijo (\$)',
                          'PERCENTAGE' => 'Porcentaje (%)',
                          _            => type,
                        };
                        return Expanded(
                          child: GestureDetector(
                            onTap: () => setState(() => _depositType = type),
                            child: AnimatedContainer(
                              duration: const Duration(milliseconds: 150),
                              margin: EdgeInsets.only(
                                right: type != 'PERCENTAGE' ? AppSpacing.xs : 0),
                              padding: const EdgeInsets.symmetric(
                                vertical: AppSpacing.sm),
                              decoration: BoxDecoration(
                                color: _depositType == type
                                    ? AppColors.goldSubtle : AppColors.slate,
                                borderRadius: AppRadius.rSm,
                                border: Border.all(
                                  color: _depositType == type
                                      ? AppColors.gold.withOpacity(0.4)
                                      : AppColors.smoke),
                              ),
                              child: Text(label,
                                textAlign: TextAlign.center,
                                style: AppTypography.labelSm.copyWith(
                                  color: _depositType == type
                                      ? AppColors.gold : AppColors.ash,
                                  fontSize: 9,
                                )),
                            ),
                          ),
                        );
                      }),
                    ],
                  ),

                  if (_depositType != 'NONE') ...[
                    const SizedBox(height: AppSpacing.sm),
                    AppTextField(
                      label: _depositType == 'FIXED'
                          ? 'Monto del depósito (MXN)'
                          : 'Porcentaje del depósito (%)',
                      hint: _depositType == 'FIXED' ? '50.00' : '20',
                      controller: _depositCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      textInputAction: TextInputAction.done,
                    ),
                  ],

                  const SizedBox(height: AppSpacing.xl),

                  AppButton(
                    label: _isEditing ? 'Guardar cambios' : 'Crear servicio',
                    loading: _isLoading,
                    onTap: _canSave ? _save : null,
                  ),

                  if (_isEditing) ...[
                    const SizedBox(height: AppSpacing.sm),
                    AppButton(
                      label: 'Desactivar servicio',
                      variant: AppButtonVariant.danger,
                      onTap: _deactivate,
                    ),
                  ],

                  const SizedBox(height: AppSpacing.md),
                ],
              ),
            ),
          ),
        ],
      ),
    ).animate().slideY(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic);
  }

  Future<void> _save() async {
    setState(() => _isLoading = true);
    HapticFeedback.mediumImpact();

    final input = CreateServiceInput(
      name:            _nameCtrl.text.trim(),
      category:        _category,
      durationMinutes: int.tryParse(_durationCtrl.text) ?? 30,
      basePrice:       _priceCtrl.text.trim(),
      depositType:     _depositType,
      depositAmount:   _depositType != 'NONE'
          ? _depositCtrl.text.trim()
          : '0',
    );

    final notifier = ref.read(homeOwnerNotifierProvider.notifier);
    final result = _isEditing
        ? await notifier.updateService(widget.existing!.id, input)
        : await notifier.createService(input);

    if (!mounted) return;
    setState(() => _isLoading = false);

    result.fold(
      (failure) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(failure.userMessage,
          style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
        backgroundColor: AppColors.error,
        behavior: SnackBarBehavior.floating,
      )),
      (_) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
            _isEditing ? 'Servicio actualizado' : 'Servicio creado',
            style: AppTypography.bodyMd.copyWith(color: AppColors.white),
          ),
          backgroundColor: AppColors.confirmed,
          behavior: SnackBarBehavior.floating,
        ));
      },
    );
  }

  Future<void> _deactivate() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Desactivar servicio', style: AppTypography.headingMd),
        content: Text(
          'El servicio ya no estará disponible para nuevas citas.',
          style: AppTypography.bodyMd,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Cancelar', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Desactivar', style: AppTypography.labelMd.copyWith(
              color: AppColors.error, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (confirm != true || !mounted) return;

    final result = await ref
        .read(homeOwnerNotifierProvider.notifier)
        .deleteService(widget.existing!.id);

    if (!mounted) return;
    result.fold(
      (f) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(f.userMessage,
          style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
        backgroundColor: AppColors.error,
        behavior: SnackBarBehavior.floating,
      )),
      (_) => Navigator.pop(context),
    );
  }
}

class _EmptyServices extends StatelessWidget {
  final VoidCallback onAdd;
  const _EmptyServices({required this.onAdd});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 72, height: 72,
            decoration: BoxDecoration(
              color: AppColors.graphite, shape: BoxShape.circle,
              border: Border.all(color: AppColors.smoke)),
            child: const Icon(Icons.content_cut_rounded,
              size: 28, color: AppColors.ash),
          ),
          const SizedBox(height: AppSpacing.md),
          Text('Sin servicios', style: AppTypography.headingSm.copyWith(
            color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text('Agrega los servicios que ofreces',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash)),
          const SizedBox(height: AppSpacing.xl),
          AppButton(
            label: '+ Agregar servicio',
            fullWidth: false,
            onTap: onAdd,
          ),
        ],
      ),
    );
  }
}

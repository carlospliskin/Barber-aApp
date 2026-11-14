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
import '../../domain/entities/loyalty_entities.dart';
import '../notifiers/home_owner_notifier.dart';

class ManageLoyaltyScreen extends ConsumerWidget {
  const ManageLoyaltyScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final program = ref.watch(homeOwnerNotifierProvider.select((s) => s.loyaltyProgram));
    final loading = ref.watch(homeOwnerNotifierProvider.select((s) => s.loyaltyLoading));
    final tenantId = ref.watch(homeOwnerNotifierProvider.select(
      (s) => s.openStatus,  // usamos cualquier selector para obtener el tenantId
    ));

    return Scaffold(
      backgroundColor: AppColors.obsidian,
      appBar: AppBar(
        backgroundColor: AppColors.obsidian,
        title: Text('Fidelización', style: AppTypography.headingMd),
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: const Icon(Icons.arrow_back_ios_rounded,
            size: 18, color: AppColors.white),
        ),
        actions: [
          if (program != null)
            GestureDetector(
              onTap: () => _showAddRuleSheet(context, ref, program),
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
                    Text('Regla', style: AppTypography.labelSm.copyWith(
                      color: AppColors.obsidian, fontWeight: FontWeight.w700)),
                  ],
                ),
              ),
            ),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator(color: AppColors.gold))
          : program == null
              ? _SetupLoyaltyView(onSetup: () {/* TODO */})
              : _ProgramView(program: program, ref: ref),
    );
  }

  void _showAddRuleSheet(
    BuildContext context, WidgetRef ref, LoyaltyProgram program) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ProviderScope(
        parent: ProviderScope.containerOf(context),
        child: _AddRuleSheet(program: program),
      ),
    );
  }
}

// ─── _ProgramView — vista del programa con sus reglas ────────────────────────

class _ProgramView extends StatelessWidget {
  final LoyaltyProgram program;
  final WidgetRef ref;

  const _ProgramView({required this.program, required this.ref});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: AppSpacing.screen,
      children: [
        // Header del programa
        GoldGlowContainer(
          child: Row(
            children: [
              Container(
                width: 48, height: 48,
                decoration: BoxDecoration(
                  color: AppColors.white, borderRadius: AppRadius.rSm,
                  boxShadow: AppColors.shadowGold,
                ),
                child: const Icon(Icons.qr_code_2_rounded,
                  size: 28, color: AppColors.obsidian),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(program.name, style: AppTypography.headingSm),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(Icons.people_outline_rounded,
                          size: 12, color: AppColors.ash),
                        const SizedBox(width: 4),
                        Text('${program.totalMembers} miembros',
                          style: AppTypography.bodySm),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: AppSpacing.xl),

        // Sección de reglas
        Row(
          children: [
            Text('REGLAS', style: AppTypography.overline.copyWith(
              color: AppColors.ash)),
            const SizedBox(width: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.xs + 2, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.goldSubtle, borderRadius: AppRadius.rPill),
              child: Text('${program.activeRules.length} activas',
                style: AppTypography.overline.copyWith(
                  color: AppColors.gold, fontSize: 8)),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.md),

        if (program.rules.isEmpty)
          _EmptyRules()
        else
          ...program.rules.asMap().entries.map((e) => Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: _RuleCard(
              rule: e.value,
              onToggle: (isActive) => ref
                  .read(homeOwnerNotifierProvider.notifier)
                  .toggleLoyaltyRule(e.value.id, isActive),
            ).animate().fadeIn(delay: Duration(milliseconds: 80 * e.key)),
          )),

        const SizedBox(height: AppSpacing.xl),

        // Tipos de reglas disponibles — a modo de referencia
        Text('TIPOS DE REGLAS DISPONIBLES',
          style: AppTypography.overline.copyWith(color: AppColors.ash)),
        const SizedBox(height: AppSpacing.md),
        ..._ruleTemplates.map((t) => _RuleTemplate(
          icon: t.$1, title: t.$2, description: t.$3)),
      ],
    );
  }

  static const _ruleTemplates = [
    (Icons.repeat_rounded,
     'Visitas acumuladas',
     'Cada N cortes, el siguiente es gratis o con descuento'),
    (Icons.local_offer_rounded,
     'Combo especial',
     'Corte + Barba a precio especial para clientes frecuentes'),
    (Icons.percent_rounded,
     'Descuento por fidelidad',
     'X% de descuento al llegar a N visitas en el mes'),
    (Icons.star_rounded,
     'Regla personalizada',
     'Define tu propia condición y recompensa'),
  ];
}

// ─── _RuleCard ────────────────────────────────────────────────────────────────

class _RuleCard extends StatelessWidget {
  final LoyaltyRule rule;
  final ValueChanged<bool> onToggle;

  const _RuleCard({required this.rule, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: rule.isActive
            ? AppColors.gold.withOpacity(0.06)
            : AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(
          color: rule.isActive
              ? AppColors.gold.withOpacity(0.3)
              : AppColors.smoke),
      ),
      child: Row(
        children: [
          // Ícono del tipo de regla
          Container(
            width: 40, height: 40,
            decoration: BoxDecoration(
              color: rule.isActive ? AppColors.goldSubtle : AppColors.slate,
              borderRadius: AppRadius.rSm,
            ),
            child: Icon(
              _iconForType(rule.type),
              color: rule.isActive ? AppColors.gold : AppColors.ash,
              size: 20,
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(rule.name, style: AppTypography.labelLg.copyWith(
                  color: rule.isActive ? AppColors.white : AppColors.ash)),
                const SizedBox(height: 2),
                Text(rule.shortDescription,
                  style: AppTypography.bodySm.copyWith(fontSize: 11)),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          // Toggle activo/inactivo
          GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              onToggle(!rule.isActive);
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 40, height: 22,
              decoration: BoxDecoration(
                color: rule.isActive ? AppColors.gold : AppColors.smoke,
                borderRadius: AppRadius.rPill,
              ),
              child: AnimatedAlign(
                duration: const Duration(milliseconds: 200),
                alignment: rule.isActive
                    ? Alignment.centerRight : Alignment.centerLeft,
                child: Container(
                  width: 18, height: 18,
                  margin: const EdgeInsets.all(2),
                  decoration: BoxDecoration(
                    color: rule.isActive
                        ? AppColors.obsidian : AppColors.graphite,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  IconData _iconForType(LoyaltyRuleType type) => switch (type) {
    LoyaltyRuleType.visitCount => Icons.repeat_rounded,
    LoyaltyRuleType.combo      => Icons.local_offer_rounded,
    LoyaltyRuleType.custom     => Icons.star_rounded,
  };
}

// ─── _AddRuleSheet — crear nueva regla ───────────────────────────────────────

class _AddRuleSheet extends ConsumerStatefulWidget {
  final LoyaltyProgram program;
  const _AddRuleSheet({required this.program});

  @override
  ConsumerState<_AddRuleSheet> createState() => _AddRuleSheetState();
}

class _AddRuleSheetState extends ConsumerState<_AddRuleSheet> {
  final _nameCtrl        = TextEditingController();
  final _triggerCtrl     = TextEditingController(text: '5');
  final _rewardValueCtrl = TextEditingController();

  LoyaltyRuleType  _type       = LoyaltyRuleType.visitCount;
  LoyaltyRewardType _rewardType = LoyaltyRewardType.freeService;
  bool _isLoading = false;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _triggerCtrl.dispose();
    _rewardValueCtrl.dispose();
    super.dispose();
  }

  bool get _canSave =>
      _nameCtrl.text.trim().isNotEmpty &&
      (_rewardType == LoyaltyRewardType.freeService ||
       _rewardValueCtrl.text.trim().isNotEmpty);

  // Auto-genera el nombre y descripción según el tipo
  String get _autoDescription {
    final trigger = _triggerCtrl.text.trim();
    switch (_type) {
      case LoyaltyRuleType.visitCount:
        switch (_rewardType) {
          case LoyaltyRewardType.freeService:
            return 'Cada $trigger visitas → Servicio gratis';
          case LoyaltyRewardType.discountPct:
            return 'Cada $trigger visitas → ${_rewardValueCtrl.text.trim()}% descuento';
          case LoyaltyRewardType.discountFixed:
            return 'Cada $trigger visitas → \$${_rewardValueCtrl.text.trim()} MXN descuento';
        }
      case LoyaltyRuleType.combo:
        return 'Combo especial para clientes frecuentes';
      case LoyaltyRuleType.custom:
        return _nameCtrl.text.trim();
    }
  }

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
                      Expanded(child: Text('Nueva regla',
                        style: AppTypography.displayMd.copyWith(fontSize: 20))),
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
                    label: 'Nombre de la regla',
                    hint: 'Ej. Club de los 5 cortes',
                    controller: _nameCtrl,
                    textInputAction: TextInputAction.next,
                    onChanged: (_) => setState(() {}),
                  ),

                  const SizedBox(height: AppSpacing.lg),

                  // Tipo de trigger
                  Text('CUÁNDO APLICA',
                    style: AppTypography.overline.copyWith(color: AppColors.ash)),
                  const SizedBox(height: AppSpacing.sm),
                  _TypeSelector<LoyaltyRuleType>(
                    options: const [
                      (LoyaltyRuleType.visitCount, 'Por visitas', Icons.repeat_rounded),
                      (LoyaltyRuleType.combo,      'Combo',       Icons.local_offer_rounded),
                      (LoyaltyRuleType.custom,     'Custom',      Icons.star_rounded),
                    ],
                    selected: _type,
                    onSelected: (t) => setState(() => _type = t),
                  ),

                  if (_type == LoyaltyRuleType.visitCount) ...[
                    const SizedBox(height: AppSpacing.md),
                    AppTextField(
                      label: 'Cada cuántas visitas',
                      hint: '5',
                      controller: _triggerCtrl,
                      keyboardType: TextInputType.number,
                      textInputAction: TextInputAction.next,
                      prefixIcon: const Icon(Icons.repeat_rounded,
                        size: 18, color: AppColors.ash),
                      onChanged: (_) => setState(() {}),
                    ),
                  ],

                  const SizedBox(height: AppSpacing.lg),

                  // Tipo de recompensa
                  Text('BENEFICIO',
                    style: AppTypography.overline.copyWith(color: AppColors.ash)),
                  const SizedBox(height: AppSpacing.sm),
                  _TypeSelector<LoyaltyRewardType>(
                    options: const [
                      (LoyaltyRewardType.freeService,   'Servicio gratis', Icons.card_giftcard_rounded),
                      (LoyaltyRewardType.discountPct,   'Descuento %',     Icons.percent_rounded),
                      (LoyaltyRewardType.discountFixed, 'Descuento \$',    Icons.attach_money_rounded),
                    ],
                    selected: _rewardType,
                    onSelected: (t) => setState(() => _rewardType = t),
                  ),

                  if (_rewardType != LoyaltyRewardType.freeService) ...[
                    const SizedBox(height: AppSpacing.md),
                    AppTextField(
                      label: _rewardType == LoyaltyRewardType.discountPct
                          ? 'Porcentaje de descuento'
                          : 'Monto de descuento (MXN)',
                      hint: _rewardType == LoyaltyRewardType.discountPct
                          ? '20' : '50',
                      controller: _rewardValueCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      textInputAction: TextInputAction.done,
                      onChanged: (_) => setState(() {}),
                    ),
                  ],

                  // Preview de la descripción
                  if (_nameCtrl.text.isNotEmpty) ...[
                    const SizedBox(height: AppSpacing.md),
                    GoldGlowContainer(
                      child: Row(
                        children: [
                          const Icon(Icons.preview_rounded,
                            size: 16, color: AppColors.gold),
                          const SizedBox(width: AppSpacing.sm),
                          Expanded(
                            child: Text(_autoDescription,
                              style: AppTypography.bodyMd.copyWith(
                                color: AppColors.pearl)),
                          ),
                        ],
                      ),
                    ),
                  ],

                  const SizedBox(height: AppSpacing.xl),

                  AppButton(
                    label: 'Crear regla',
                    loading: _isLoading,
                    onTap: _canSave ? _save : null,
                  ),
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

    final rule = LoyaltyRule(
      id: '',                       // el backend lo asigna
      programId: widget.program.id,
      name: _nameCtrl.text.trim(),
      description: _autoDescription,
      type: _type,
      triggerValue: _type == LoyaltyRuleType.visitCount
          ? int.tryParse(_triggerCtrl.text) : null,
      rewardType: _rewardType,
      rewardValue: _rewardType == LoyaltyRewardType.freeService
          ? null : double.tryParse(_rewardValueCtrl.text),
    );

    await ref.read(homeOwnerNotifierProvider.notifier).addLoyaltyRule(rule);

    if (mounted) {
      setState(() => _isLoading = false);
      Navigator.pop(context);
    }
  }
}

// ─── _TypeSelector genérico ───────────────────────────────────────────────────

class _TypeSelector<T> extends StatelessWidget {
  final List<(T, String, IconData)> options;
  final T selected;
  final ValueChanged<T> onSelected;

  const _TypeSelector({
    required this.options,
    required this.selected,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: options.map((opt) {
        final (value, label, icon) = opt;
        final isSelected = selected == value;
        return Expanded(
          child: GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              onSelected(value);
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              margin: EdgeInsets.only(
                right: opt != options.last ? AppSpacing.xs : 0),
              padding: const EdgeInsets.symmetric(
                vertical: AppSpacing.sm, horizontal: AppSpacing.xs),
              decoration: BoxDecoration(
                color: isSelected ? AppColors.goldSubtle : AppColors.slate,
                borderRadius: AppRadius.rSm,
                border: Border.all(
                  color: isSelected
                      ? AppColors.gold.withOpacity(0.4)
                      : AppColors.smoke),
              ),
              child: Column(
                children: [
                  Icon(icon, size: 16,
                    color: isSelected ? AppColors.gold : AppColors.ash),
                  const SizedBox(height: 4),
                  Text(label, style: AppTypography.overline.copyWith(
                    color: isSelected ? AppColors.gold : AppColors.ash,
                    fontSize: 8,
                  ), textAlign: TextAlign.center),
                ],
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

class _RuleTemplate extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;

  const _RuleTemplate({
    required this.icon,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Row(
        children: [
          Container(
            width: 36, height: 36,
            decoration: BoxDecoration(
              color: AppColors.slate, borderRadius: AppRadius.rSm),
            child: Icon(icon, size: 18, color: AppColors.ash),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppTypography.labelLg.copyWith(
                  color: AppColors.silver)),
                Text(description, style: AppTypography.bodySm.copyWith(
                  fontSize: 11)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyRules extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.xl),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Column(
        children: [
          const Icon(Icons.star_outline_rounded,
            size: 32, color: AppColors.ash),
          const SizedBox(height: AppSpacing.md),
          Text('Sin reglas aún', style: AppTypography.headingSm.copyWith(
            color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text('Crea tu primera regla de fidelización',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash),
            textAlign: TextAlign.center),
        ],
      ),
    );
  }
}

class _SetupLoyaltyView extends StatelessWidget {
  final VoidCallback onSetup;
  const _SetupLoyaltyView({required this.onSetup});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: AppSpacing.screen,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80, height: 80,
              decoration: BoxDecoration(
                color: AppColors.goldSubtle, shape: BoxShape.circle,
                border: Border.all(color: AppColors.gold.withOpacity(0.4))),
              child: const Icon(Icons.card_giftcard_rounded,
                size: 36, color: AppColors.gold),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('Programa de fidelización',
              style: AppTypography.headingLg, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Retén a tus clientes con beneficios exclusivos.\n'
              'Crea reglas como "cada 5 cortes, el 6to es gratis".',
              style: AppTypography.bodyMd.copyWith(color: AppColors.silver),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.xxl),
            AppButton(label: 'Crear programa de lealtad', onTap: onSetup),
          ],
        ),
      ),
    );
  }
}

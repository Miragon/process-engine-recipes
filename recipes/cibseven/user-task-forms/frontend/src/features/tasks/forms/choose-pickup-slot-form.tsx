import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useCompleteTask, type OpenTask } from "../api/tasks";

const pickupSlots = {
  FRIDAY_AFTERNOON: "Friday, 14:00 – 18:00",
  SATURDAY_MORNING: "Saturday, 09:00 – 12:00",
  MONDAY_EVENING: "Monday, 17:00 – 19:00",
} as const;

type PickupSlot = keyof typeof pickupSlots;

const choosePickupSlotSchema = z.object({
  pickupSlot: z.enum(Object.keys(pickupSlots) as [PickupSlot, ...PickupSlot[]], {
    error: "Choose a pickup slot.",
  }),
});

type ChoosePickupSlotValues = z.infer<typeof choosePickupSlotSchema>;

export function ChoosePickupSlotForm({ task }: { task: OpenTask }) {
  const completeTask = useCompleteTask(task.id);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ChoosePickupSlotValues>({ resolver: zodResolver(choosePickupSlotSchema) });

  const submit = handleSubmit((values) => completeTask.mutate(values));

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body font-medium">Your bike is ready. When do you pick it up?</legend>
        {Object.entries(pickupSlots).map(([slot, label]) => (
          <label key={slot} className="flex items-center gap-3 text-body">
            <input type="radio" value={slot} {...register("pickupSlot")} className="accent-blau" />
            {label}
          </label>
        ))}
        {errors.pickupSlot ? (
          <span role="alert" className="field-error">
            {errors.pickupSlot.message}
          </span>
        ) : null}
      </fieldset>

      <button type="submit" className="button-primary" disabled={completeTask.isPending}>
        {completeTask.isPending ? "Saving…" : "Book pickup slot"}
      </button>
    </form>
  );
}
